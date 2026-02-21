package com.eduplatform.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String REDIS_RATE_LIMIT_KEY_PREFIX = "gateway:ratelimit:";

    private final Cache<String, RateLimiter> limiterCache;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final int permitsPerSecond;
    private final int windowSeconds;
    private final boolean trustForwardedHeaders;
    private final Set<String> trustedProxyIps;
    private final boolean redisEnabled;

    public RateLimitFilter(double permitsPerSecond, long expireMinutes, long maxSize, boolean trustForwardedHeaders) {
        this(null, permitsPerSecond, expireMinutes, maxSize, trustForwardedHeaders, 1, false, Collections.emptySet());
    }

    public RateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            double permitsPerSecond,
            long expireMinutes,
            long maxSize,
            boolean trustForwardedHeaders,
            int windowSeconds,
            boolean redisEnabled) {
        this(redisTemplate, permitsPerSecond, expireMinutes, maxSize, trustForwardedHeaders, windowSeconds, redisEnabled,
                Collections.emptySet());
    }

    public RateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            double permitsPerSecond,
            long expireMinutes,
            long maxSize,
            boolean trustForwardedHeaders,
            int windowSeconds,
            boolean redisEnabled,
            Set<String> trustedProxyIps) {
        this.redisTemplate = redisTemplate;
        this.permitsPerSecond = Math.max(1, (int) Math.floor(permitsPerSecond));
        this.windowSeconds = Math.max(1, windowSeconds);
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxyIps = trustedProxyIps == null
                ? Collections.emptySet()
                : trustedProxyIps.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(Collectors.toSet());
        this.redisEnabled = redisEnabled && redisTemplate != null;
        this.limiterCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(expireMinutes))
                .maximumSize(maxSize)
                .build();

        if (redisEnabled && redisTemplate == null) {
            log.warn("网关限流已开启 Redis 模式，但未注入 RedisTemplate，将自动降级为本地限流");
        }
        if (trustForwardedHeaders && this.trustedProxyIps.isEmpty()) {
            log.warn("网关限流开启了转发头信任，但未配置受信代理 IP，将仅使用 remoteAddress 作为限流键");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientKey(exchange);
        return allowRequest(clientIp, exchange)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }

                    log.warn("触发网关限流: ip={}, path={}", clientIp, exchange.getRequest().getPath());
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add("X-Rate-Limit-Exceeded", "true");
                    exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(windowSeconds));
                    return exchange.getResponse().setComplete();
                });
    }

    /**
     * 优先使用 Redis 分布式限流，失败时自动降级到本地限流，保障网关可用性。
     */
    private Mono<Boolean> allowRequest(String clientIp, ServerWebExchange exchange) {
        if (!redisEnabled) {
            return Mono.just(allowByLocal(clientIp));
        }
        return allowByRedis(clientIp).onErrorResume(e -> {
            log.warn("Redis限流失败，降级为本地限流: ip={}, path={}, error={}",
                    clientIp,
                    exchange.getRequest().getPath(),
                    e.getMessage());
            return Mono.just(allowByLocal(clientIp));
        });
    }

    /**
     * 本地令牌桶限流（降级兜底）。
     */
    private boolean allowByLocal(String clientIp) {
        RateLimiter limiter = limiterCache.get(clientIp, key -> RateLimiter.create(permitsPerSecond));
        return limiter != null && limiter.tryAcquire();
    }

    /**
     * Redis 固定时间窗限流（多实例共享）。
     */
    private Mono<Boolean> allowByRedis(String clientIp) {
        String redisKey = buildRedisWindowKey(clientIp);
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(counter -> {
                    if (counter == null) {
                        return Mono.just(false);
                    }

                    Mono<Boolean> expireOperation = Mono.just(true);
                    if (counter == 1L) {
                        // 首次命中窗口时设置过期时间，避免 Redis Key 无限增长。
                        expireOperation = redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds + 1))
                                .onErrorResume(e -> {
                                    log.warn("设置Redis限流Key过期时间失败: key={}, error={}", redisKey, e.getMessage());
                                    return Mono.just(false);
                                });
                    }

                    long currentCounter = counter;
                    return expireOperation.thenReturn(currentCounter <= permitsPerSecond);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 构建 Redis 限流窗口键。
     */
    private String buildRedisWindowKey(String clientIp) {
        long windowId = Instant.now().getEpochSecond() / windowSeconds;
        return REDIS_RATE_LIMIT_KEY_PREFIX + clientIp + ":" + windowId;
    }

    /**
     * 解析限流键。
     * 默认只使用 remoteAddress，避免客户端伪造 X-Forwarded-For 绕过限流。
     * 仅当来源地址命中受信代理列表时，才允许使用转发头中的客户端地址。
     */
    String resolveClientKey(ServerWebExchange exchange) {
        String remoteIp = resolveRemoteAddress(exchange);
        if (shouldUseForwardedHeaders(remoteIp)) {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(xForwardedFor)) {
                String forwardedIp = xForwardedFor.split(",")[0].trim();
                if (StringUtils.hasText(forwardedIp)) {
                    return forwardedIp;
                }
            }

            String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(xRealIp)) {
                return xRealIp.trim();
            }
        }

        if (StringUtils.hasText(remoteIp)) {
            return remoteIp;
        }
        return "unknown";
    }

    /**
     * 仅允许受信代理透传真实客户端地址，防止直接连接客户端伪造转发头绕过限流。
     */
    private boolean shouldUseForwardedHeaders(String remoteIp) {
        return trustForwardedHeaders
                && StringUtils.hasText(remoteIp)
                && trustedProxyIps.contains(remoteIp);
    }

    /**
     * 提取直接连接地址，作为默认可信来源。
     */
    private String resolveRemoteAddress(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() == null) {
            return null;
        }
        if (exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return exchange.getRequest().getRemoteAddress().getHostString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

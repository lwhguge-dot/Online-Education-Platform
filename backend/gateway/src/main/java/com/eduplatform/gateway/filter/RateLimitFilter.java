package com.eduplatform.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final Cache<String, RateLimiter> limiterCache;
    private final double permitsPerSecond;

    public RateLimitFilter(double permitsPerSecond, long expireMinutes, long maxSize) {
        this.permitsPerSecond = permitsPerSecond;
        this.limiterCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(expireMinutes))
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = getClientIp(exchange);
        RateLimiter limiter = limiterCache.get(clientIp, key -> RateLimiter.create(permitsPerSecond));

        if (limiter != null && limiter.tryAcquire()) {
            return chain.filter(exchange);
        }

        log.warn("触发网关限流: ip={}, path={}", clientIp, exchange.getRequest().getPath());
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-Rate-Limit-Exceeded", "true");
        exchange.getResponse().getHeaders().add("Retry-After", "1");
        return exchange.getResponse().setComplete();
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

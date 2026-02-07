package com.eduplatform.gateway.filter;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于IP的内存令牌桶限流过滤器。
 * 说明：使用 Guava RateLimiter 实现，作为网关第一道保护防线。
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /**
     * 每个IP对应一个限流器。
     */
    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * 每秒允许的请求数（令牌补充速率）。
     */
    private final double permitsPerSecond;

    public RateLimitFilter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取客户端IP
        String clientIp = getClientIp(exchange);

        // 获取或创建该IP的限流器
        RateLimiter limiter = limiters.computeIfAbsent(clientIp,
                k -> RateLimiter.create(permitsPerSecond));

        // 尝试获取令牌（非阻塞）
        if (limiter.tryAcquire()) {
            return chain.filter(exchange);
        } else {
            // 限流触发，返回 429 Too Many Requests
            log.warn("触发限流：IP={}, Path={}", clientIp, exchange.getRequest().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-Rate-Limit-Exceeded", "true");
            exchange.getResponse().getHeaders().add("Retry-After", "1");
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 获取客户端真实IP。
     * 优先从 X-Forwarded-For 头获取。
     */
    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 取第一个IP（最原始的客户端IP）
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 降级到直连IP
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    @Override
    public int getOrder() {
        // 最高优先级，在其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 清理长时间未使用的限流器（可选：防止内存泄漏）。
     * 可在定时任务中调用。
     */
    public void cleanupIdleLimiters() {
        // 简单实现：当限流器数量过多时清理
        if (limiters.size() > 10000) {
            log.info("清理限流器缓存，当前数量：{}", limiters.size());
            limiters.clear();
        }
    }
}

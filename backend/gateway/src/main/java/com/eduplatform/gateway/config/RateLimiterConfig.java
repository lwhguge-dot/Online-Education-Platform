package com.eduplatform.gateway.config;

import com.eduplatform.gateway.filter.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限流配置类。
 * 设计意图：通过 IP 级限流保护网关与下游服务，避免突发流量造成雪崩。
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 创建限流过滤器。
     * 默认限流：每 IP 每秒 200 个请求，作为保护性阈值。
     */
    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(200); // 每秒200个请求
    }
}

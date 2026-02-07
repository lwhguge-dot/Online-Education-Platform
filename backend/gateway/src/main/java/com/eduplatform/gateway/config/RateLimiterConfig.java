package com.eduplatform.gateway.config;

import com.eduplatform.gateway.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Value("${gateway.rate-limit.permits-per-second:200}")
    private double permitsPerSecond;

    @Value("${gateway.rate-limit.cache-expire-minutes:30}")
    private long cacheExpireMinutes;

    @Value("${gateway.rate-limit.cache-max-size:100000}")
    private long cacheMaxSize;

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(permitsPerSecond, cacheExpireMinutes, cacheMaxSize);
    }
}

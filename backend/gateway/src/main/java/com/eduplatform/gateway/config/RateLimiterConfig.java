package com.eduplatform.gateway.config;

import com.eduplatform.gateway.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class RateLimiterConfig {

    @Value("${gateway.rate-limit.permits-per-second:200}")
    private double permitsPerSecond;

    @Value("${gateway.rate-limit.cache-expire-minutes:30}")
    private long cacheExpireMinutes;

    @Value("${gateway.rate-limit.cache-max-size:100000}")
    private long cacheMaxSize;

    @Value("${gateway.rate-limit.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Value("${gateway.rate-limit.redis-enabled:true}")
    private boolean redisEnabled;

    @Value("${gateway.rate-limit.window-seconds:1}")
    private int windowSeconds;

    @Value("${gateway.rate-limit.trusted-proxies:}")
    private String trustedProxies;

    @Bean
    public RateLimitFilter rateLimitFilter(ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {
        return new RateLimitFilter(
                redisTemplateProvider.getIfAvailable(),
                permitsPerSecond,
                cacheExpireMinutes,
                cacheMaxSize,
                trustForwardedHeaders,
                windowSeconds,
                redisEnabled,
                parseTrustedProxyIps());
    }

    /**
     * 解析受信代理地址列表，仅这些来源允许透传 X-Forwarded-For/X-Real-IP。
     */
    private Set<String> parseTrustedProxyIps() {
        if (!StringUtils.hasText(trustedProxies)) {
            return Collections.emptySet();
        }
        return Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}

package com.eduplatform.course.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部调用鉴权配置。
 * 设计意图：
 * 1. 为跨服务的高危级联接口自动注入内部令牌。
 * 2. 仅对 /cascade/ 路径注入，避免无关请求携带敏感头。
 */
@Configuration
public class InternalApiFeignConfig {

    @Value("${security.internal-token}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalApiTokenInterceptor() {
        return requestTemplate -> {
            String path = requestTemplate.path();
            if (path != null && path.contains("/cascade/")) {
                requestTemplate.header("X-Internal-Token", internalToken);
            }
        };
    }
}


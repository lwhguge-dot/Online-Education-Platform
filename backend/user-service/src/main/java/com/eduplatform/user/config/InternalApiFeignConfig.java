package com.eduplatform.user.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部调用鉴权配置。
 * 设计意图：
 * 1. user-service 调用课程级联接口时自动携带内部令牌。
 * 2. 仅对 /cascade/ 路径注入，控制最小暴露面。
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


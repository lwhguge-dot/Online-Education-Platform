package com.eduplatform.homework.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Homework 服务内部 Feign 调用安全配置。
 * 设计目的：为调用 user-service 的内部接口自动附加内部令牌，避免外部伪造调用。
 */
@Configuration
public class InternalApiFeignConfig {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    @Value("${security.internal-token}")
    private String internalToken;

    /**
     * 自动给通知发送接口附加内部令牌。
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            String path = template.path();
            if (path == null || !path.startsWith("/api/notifications/")) {
                return;
            }
            if (!StringUtils.hasText(internalToken)) {
                return;
            }
            template.header(HEADER_INTERNAL_TOKEN, internalToken);
        };
    }
}


package com.eduplatform.progress.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Progress 服务内部 Feign 调用安全配置。
 * 设计目的：为调用 homework-service 的内部接口自动附加内部令牌。
 */
@Configuration
public class InternalApiFeignConfig {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    @Value("${security.internal-token}")
    private String internalToken;

    /**
     * 仅对内部解锁接口注入内部令牌。
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            String path = template.path();
            if (path == null || !path.startsWith("/api/homeworks/unlock")) {
                return;
            }
            if (!StringUtils.hasText(internalToken)) {
                return;
            }
            template.header(HEADER_INTERNAL_TOKEN, internalToken);
        };
    }
}


package com.eduplatform.user.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部调用鉴权配置。
 * 设计意图：
 * 1. 对所有 Feign 调用自动注入 X-Internal-Token，使下游服务可识别为内部可信调用。
 * 2. cascade 接口由网关 + 业务双重校验 token；普通接口由业务自行决定是否放行内部调用。
 */
@Configuration
public class InternalApiFeignConfig {

    @Value("${security.internal-token}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalApiTokenInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Internal-Token", internalToken);
    }
}


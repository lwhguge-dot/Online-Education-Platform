package com.eduplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动入口。
 * 设计意图：统一路由、跨域与限流入口，并注册到服务发现中心。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    /**
     * 应用主入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

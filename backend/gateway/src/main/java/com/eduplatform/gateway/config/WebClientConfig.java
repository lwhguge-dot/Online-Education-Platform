package com.eduplatform.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 网关内部调用配置。
 * 说明：启用带负载均衡能力的 WebClient，用于按服务名访问下游微服务。
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(3))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}

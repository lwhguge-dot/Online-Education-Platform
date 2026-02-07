package com.eduplatform.homework.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis 基础配置类
 * homework-service 使用 Spring Boot 默认的 Redis 自动配置。
 * Stream 消费者配置见 {@link RedisStreamConfig}。
 *
 * @author Antigravity
 */
@Configuration
public class RedisConfig {
    // Spring Boot 自动配置已提供 StringRedisTemplate Bean
    // Stream 相关消费者配置由 RedisStreamConfig 管理
}

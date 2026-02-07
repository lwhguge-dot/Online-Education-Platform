package com.eduplatform.progress.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Stream 配置类
 * progress-service 作为生产者发布 CHAPTER_COMPLETED 事件。
 * Spring Boot 自动配置已提供 StringRedisTemplate，无需额外声明。
 *
 * @author Antigravity
 */
@Slf4j
@Configuration
public class RedisStreamConfig {
    // RedisStreamPublisher 所需的 StringRedisTemplate 由 Spring Boot 自动配置提供
}

package com.eduplatform.course.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Stream 配置类
 * course-service 仅作为事件生产者（发布选课/退课事件），
 * 不需要配置消费者容器。Spring Boot 自动配置已提供 StringRedisTemplate。
 *
 * @author Antigravity
 */
@Slf4j
@Configuration
public class RedisStreamConfig {
    // RedisStreamPublisher 所需的 StringRedisTemplate 由 Spring Boot 自动配置提供
}

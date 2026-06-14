package com.eduplatform.homework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 作业讨论配置。
 * 用于配置主观题讨论的超时阈值与统计维度，支持通过 application.yml 动态调整。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edu.discussion")
public class DiscussionConfig {

    /**
     * 待回答讨论的超时阈值（小时）。
     * 超过此时长未回复的 pending 讨论标记为 overdue。
     * 默认值：48
     */
    private long overdueThresholdHours = 48;
}

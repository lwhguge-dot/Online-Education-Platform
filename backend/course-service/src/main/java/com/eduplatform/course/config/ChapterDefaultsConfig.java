package com.eduplatform.course.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 章节默认值配置。
 * 用于配置章节创建时的默认解锁阈值与测验默认分值，支持通过 application.yml 动态调整。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edu.chapter-defaults")
public class ChapterDefaultsConfig {

    /**
     * 默认视频完成率阈值（0.0 ~ 1.0）。
     * 学生观看视频达到该比例视为通过，可解锁后续关卡。
     * 默认值：0.9（90%）
     */
    private BigDecimal unlockVideoRate = BigDecimal.valueOf(0.9);

    /**
     * 默认测验及格分数。
     * 默认值：60
     */
    private Integer unlockQuizScore = 60;

    /**
     * 章节默认启用状态（1=启用，0=禁用）。
     * 默认值：1
     */
    private Integer defaultStatus = 1;

    /**
     * 章节测验题默认分值。
     * 默认值：10
     */
    private Integer defaultQuizScore = 10;
}

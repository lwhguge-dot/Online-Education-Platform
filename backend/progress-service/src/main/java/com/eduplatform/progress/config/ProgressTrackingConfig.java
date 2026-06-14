package com.eduplatform.progress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 学习进度上报配置。
 * 用于配置视频进度上报的反作弊阈值与异步落库策略，支持通过 application.yml 动态调整。
 *
 * <p>关键参数：
 * <ul>
 *   <li>{@link #fastForwardToleranceRatio} + {@link #fastForwardToleranceSeconds}：
 *       异常快进判定阈值，上报进度增量超过 {@code elapsedRealTimeSec * ratio + seconds} 视为作弊。</li>
 *   <li>{@link #dbSyncIntervalMs}：进度写入 DB 的最小时间间隔，期间仅写 Redis 缓存以降低 DB 压力。</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edu.progress-tracking")
public class ProgressTrackingConfig {

    /**
     * 异常快进判定的时间倍率。
     * 上报进度增量若超过 {@code 实际耗时 * 倍率 + 容错秒数}，视为快进。
     * 默认值：1.5
     */
    private double fastForwardToleranceRatio = 1.5;

    /**
     * 异常快进判定的容错秒数。
     * 与 {@link #fastForwardToleranceRatio} 共同构成判定阈值。
     * 默认值：5
     */
    private long fastForwardToleranceSeconds = 5;

    /**
     * 进度写入 DB 的最小时间间隔（毫秒）。
     * 间隔内的进度上报仅写 Redis，超过间隔才同步落库。
     * 默认值：30000（30 秒）
     */
    private long dbSyncIntervalMs = 30000;
}

package com.eduplatform.course.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 学习状态评估配置类
 * 用于配置学情分类的阈值和权重，支持通过 application.yml 动态调整。
 *
 * 评估模型说明：
 * 1. 活跃度优先：超过 inactiveDays 天未学习的学生强制标记为 inactive
 * 2. 综合评分：活跃度(activityWeight) + 进度(progressWeight) + 测验(quizWeight)
 * 3. 分类阈值：excellent >= excellentThreshold, good >= goodThreshold, at-risk < atRiskThreshold
 *
 * @author Antigravity
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edu.learning-status")
public class LearningStatusConfig {

    /**
     * 静默期阈值（天数）：超过此天数未学习的学生标记为 inactive
     * 默认值：7天
     */
    private int inactiveDays = 7;

    /**
     * 优秀阈值（综合得分）：综合得分 >= 此值为 excellent
     * 默认值：80
     */
    private int excellentThreshold = 80;

    /**
     * 良好阈值（综合得分）：综合得分 >= 此值为 good
     * 默认值：50
     */
    private int goodThreshold = 50;

    /**
     * 风险阈值（综合得分）：综合得分 < 此值为 at-risk
     * 默认值：30
     */
    private int atRiskThreshold = 30;

    /**
     * 活跃度权重（0.0 ~ 1.0）
     * 默认值：0.3（30%）
     */
    private double activityWeight = 0.3;

    /**
     * 学习进度权重（0.0 ~ 1.0）
     * 默认值：0.4（40%）
     */
    private double progressWeight = 0.4;

    /**
     * 测验成绩权重（0.0 ~ 1.0）
     * 默认值：0.3（30%）
     */
    private double quizWeight = 0.3;

    /**
     * 默认测验成绩（暂无测验数据时使用）
     * 默认值：60分
     */
    private int defaultQuizScore = 60;

    /**
     * 活跃度每日衰减值（分/天）
     * 从当天 100 分开始，每增加一天未学习扣除此值
     * 默认值：10
     */
    private int activityDecayPerDay = 10;
}

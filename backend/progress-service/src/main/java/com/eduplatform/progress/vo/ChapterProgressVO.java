package com.eduplatform.progress.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 章节进度视图对象
 * 用于向前端返回用户的学习进度信息，实现数据隔离。
 *
 * @author Antigravity
 */
@Data
public class ChapterProgressVO {

    /**
     * 进度ID
     */
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 视频观看进度 (秒)
     */
    private Integer videoWatchTime;

    /**
     * 视频观看位置 (秒，用于续播)
     */
    private Integer lastPosition;

    /**
     * 视频观看利率 (0.00 - 1.00)
     */
    private BigDecimal videoRate;

    /**
     * 测验最高得分
     */
    private Integer quizScore;

    /**
     * 测验提交时间
     */
    private LocalDateTime quizSubmittedAt;

    /**
     * 是否已完成 (1: 是, 0: 否)
     */
    private Integer isCompleted;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
}

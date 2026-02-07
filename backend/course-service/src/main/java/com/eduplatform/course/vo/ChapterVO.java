package com.eduplatform.course.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 章节视图对象
 * 用于向前端展示课程章节详情，实现数据隔离。
 *
 * @author Antigravity
 */
@Data
public class ChapterVO {

    /**
     * 章节ID
     */
    private Long id;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 章节标题
     */
    private String title;

    /**
     * 章节详细描述
     */
    private String description;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 视频资源地址
     */
    private String videoUrl;

    /**
     * 视频时长 (秒)
     */
    private Integer videoDuration;

    /**
     * 解锁所需视频观看率 (0.0 - 1.0)
     */
    private BigDecimal unlockVideoRate;

    /**
     * 解锁所需测验分数
     */
    private Integer unlockQuizScore;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

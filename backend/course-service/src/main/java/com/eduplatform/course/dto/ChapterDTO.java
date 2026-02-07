package com.eduplatform.course.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 章节数据传输对象
 * 用于接收前端创建或更新章节的请求参数。
 *
 * @author Antigravity
 */
@Data
public class ChapterDTO {

    /**
     * 章节ID (更新时必填)
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
     * 解锁所需视频观看率
     */
    private BigDecimal unlockVideoRate;

    /**
     * 解锁所需测验分数
     */
    private Integer unlockQuizScore;

    /**
     * 状态 (1: 启用, 0: 禁用)
     */
    private Integer status;
}

package com.eduplatform.course.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @NotNull(message = "courseId不能为空")
    @Positive(message = "courseId必须为正数")
    private Long courseId;

    /**
     * 章节标题
     */
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    /**
     * 章节详细描述
     */
    @Size(max = 2000, message = "description长度不能超过2000")
    private String description;

    /**
     * 排序权重
     */
    @Positive(message = "sortOrder必须为正数")
    private Integer sortOrder;

    /**
     * 视频资源地址
     */
    @Size(max = 1000, message = "videoUrl长度不能超过1000")
    private String videoUrl;

    /**
     * 视频时长 (秒)
     */
    @PositiveOrZero(message = "videoDuration不能为负数")
    private Integer videoDuration;

    /**
     * 解锁所需视频观看率
     */
    @DecimalMin(value = "0.0", message = "unlockVideoRate不能小于0")
    @DecimalMax(value = "1.0", message = "unlockVideoRate不能大于1")
    private BigDecimal unlockVideoRate;

    /**
     * 解锁所需测验分数
     */
    @PositiveOrZero(message = "unlockQuizScore不能为负数")
    private Integer unlockQuizScore;

    /**
     * 状态 (1: 启用, 0: 禁用)
     */
    @Min(value = 0, message = "status只能为0或1")
    @Max(value = 1, message = "status只能为0或1")
    private Integer status;
}

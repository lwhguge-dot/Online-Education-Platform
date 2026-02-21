package com.eduplatform.course.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程更新请求 DTO。
 * 设计意图：保持“局部更新”语义，同时提供字段边界校验。
 */
@Data
public class CourseUpdateRequest {

    /**
     * 课程标题（可选更新）。
     */
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    /**
     * 课程描述（可选更新）。
     */
    @Size(max = 10000, message = "description长度不能超过10000")
    private String description;

    /**
     * 学科分类（可选更新）。
     */
    @Size(max = 50, message = "subject长度不能超过50")
    private String subject;

    /**
     * 封面地址（可选更新）。
     */
    @Size(max = 500, message = "coverImage长度不能超过500")
    private String coverImage;

    /**
     * 教师 ID（由服务端覆盖，防止伪造）。
     */
    @Positive(message = "teacherId必须为正数")
    private Long teacherId;
}

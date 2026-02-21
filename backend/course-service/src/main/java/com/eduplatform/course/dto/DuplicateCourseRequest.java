package com.eduplatform.course.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程复制请求。
 */
@Data
public class DuplicateCourseRequest {

    /**
     * 新课程标题（可选）。
     */
    @Size(max = 255, message = "title长度不能超过255")
    private String title;

    /**
     * 目标教师ID（仅管理员可指定）。
     */
    @Positive(message = "teacherId必须为正数")
    private Long teacherId;
}

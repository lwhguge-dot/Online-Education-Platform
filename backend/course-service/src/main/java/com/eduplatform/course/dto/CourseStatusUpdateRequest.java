package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程状态更新请求。
 */
@Data
public class CourseStatusUpdateRequest {

    /**
     * 目标状态。
     */
    @NotBlank(message = "status不能为空")
    @Size(max = 50, message = "status长度不能超过50")
    private String status;
}

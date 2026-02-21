package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 解除禁言请求。
 */
@Data
public class UnmuteUserRequest {

    /**
     * 用户ID。
     */
    @NotNull(message = "userId不能为空")
    @Positive(message = "userId必须为正数")
    private Long userId;

    /**
     * 课程ID。
     */
    @NotNull(message = "courseId不能为空")
    @Positive(message = "courseId必须为正数")
    private Long courseId;
}

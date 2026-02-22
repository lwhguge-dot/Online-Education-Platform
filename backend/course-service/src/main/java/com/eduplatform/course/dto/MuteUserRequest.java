package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 禁言请求。
 */
@Data
public class MuteUserRequest {

    /**
     * 被禁言用户ID。
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

    /**
     * 禁言原因。
     */
    @Size(max = 500, message = "reason长度不能超过500")
    private String reason;
}

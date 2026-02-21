package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 屏蔽词检查请求。
 */
@Data
public class CheckBlockedWordRequest {

    /**
     * 待检查内容。
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 5000, message = "content长度不能超过5000")
    private String content;

    /**
     * 课程ID（可选）。
     */
    @Positive(message = "courseId必须为正数")
    private Long courseId;
}

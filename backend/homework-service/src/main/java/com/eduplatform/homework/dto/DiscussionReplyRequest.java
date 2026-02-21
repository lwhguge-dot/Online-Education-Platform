package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 讨论回复请求。
 */
@Data
public class DiscussionReplyRequest {

    /**
     * 回复内容。
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 2000, message = "content长度不能超过2000")
    private String content;

    /**
     * 课程ID（可选）。
     */
    @Positive(message = "courseId必须为正数")
    private Long courseId;

    /**
     * 章节ID（可选）。
     */
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;
}

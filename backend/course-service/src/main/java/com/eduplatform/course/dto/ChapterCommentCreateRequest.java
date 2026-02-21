package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 章节评论创建请求。
 */
@Data
public class ChapterCommentCreateRequest {

    /**
     * 章节ID。
     */
    @NotNull(message = "chapterId不能为空")
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;

    /**
     * 课程ID。
     */
    @NotNull(message = "courseId不能为空")
    @Positive(message = "courseId必须为正数")
    private Long courseId;

    /**
     * 评论内容。
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 2000, message = "content长度不能超过2000")
    private String content;

    /**
     * 父评论ID（可选）。
     */
    @Positive(message = "parentId必须为正数")
    private Long parentId;
}

package com.eduplatform.homework.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 复制作业请求体。
 * 说明：字段均为可选，未传时沿用源作业默认值。
 */
@Data
public class DuplicateHomeworkRequest {

    /**
     * 目标章节ID（可选）。
     */
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;

    /**
     * 新作业标题（可选）。
     */
    @Size(max = 100, message = "title长度不能超过100")
    private String title;
}

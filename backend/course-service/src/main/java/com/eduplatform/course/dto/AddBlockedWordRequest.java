package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 屏蔽词新增请求。
 */
@Data
public class AddBlockedWordRequest {

    /**
     * 屏蔽词内容。
     */
    @NotBlank(message = "word不能为空")
    @Size(max = 100, message = "word长度不能超过100")
    private String word;

    /**
     * 作用域（global/course）。
     */
    @Size(max = 20, message = "scope长度不能超过20")
    private String scope;

    /**
     * 课程ID（course 作用域可选）。
     */
    @Positive(message = "courseId必须为正数")
    private Long courseId;
}

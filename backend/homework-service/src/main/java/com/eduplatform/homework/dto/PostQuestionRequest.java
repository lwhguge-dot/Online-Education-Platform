package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师发布题目请求。
 */
@Data
public class PostQuestionRequest {

    /**
     * 题目内容。
     */
    @NotBlank(message = "questionContent不能为空")
    @Size(max = 5000, message = "questionContent长度不能超过5000")
    private String questionContent;
}

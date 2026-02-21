package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布答案请求。
 */
@Data
public class PublishAnswerRequest {

    /**
     * 答案内容。
     */
    @NotBlank(message = "answerContent不能为空")
    @Size(max = 5000, message = "answerContent长度不能超过5000")
    private String answerContent;
}

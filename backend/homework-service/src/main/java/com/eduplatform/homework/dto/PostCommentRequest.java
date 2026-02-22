package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论发布请求。
 */
@Data
public class PostCommentRequest {

    /**
     * 评论内容。
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 2000, message = "content长度不能超过2000")
    private String content;
}

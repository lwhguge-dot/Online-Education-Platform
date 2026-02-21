package com.eduplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单条通知发送请求。
 */
@Data
public class SendNotificationRequest {

    /**
     * 目标用户ID。
     */
    @NotNull(message = "userId不能为空")
    @Positive(message = "userId必须为正数")
    private Long userId;

    /**
     * 通知标题。
     */
    @NotBlank(message = "title不能为空")
    @Size(max = 100, message = "title长度不能超过100")
    private String title;

    /**
     * 通知内容。
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 1000, message = "content长度不能超过1000")
    private String content;

    /**
     * 通知类型，可选。
     */
    @Size(max = 50, message = "type长度不能超过50")
    private String type;
}

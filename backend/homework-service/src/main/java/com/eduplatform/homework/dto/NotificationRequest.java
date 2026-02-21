package com.eduplatform.homework.dto;

import lombok.Data;

/**
 * 通知发送请求（供 Feign 调用 user-service）。
 */
@Data
public class NotificationRequest {

    /**
     * 通知目标用户ID。
     */
    private Long userId;

    /**
     * 通知标题。
     */
    private String title;

    /**
     * 通知内容。
     */
    private String content;

    /**
     * 通知类型。
     */
    private String type;
}

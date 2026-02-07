package com.eduplatform.user.service;

import com.eduplatform.user.entity.Notification;
import com.eduplatform.user.mapper.NotificationMapper;
import com.eduplatform.user.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 统一通知服务
 * 所有异步事件消费后的通知行为统一通过此服务下发，
 * 实现"先持久化，再推送"的双保险机制：
 * 1. 将通知写入 notifications 表，保证消息不丢失
 * 2. 通过 WebSocket 实时推送给在线用户
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationWebSocketHandler webSocketHandler;

    /**
     * 发送通知（持久化 + WebSocket 推送）
     *
     * @param userId    接收用户ID
     * @param title     通知标题
     * @param content   通知内容
     * @param type      通知类型（system / course / homework / comment）
     * @param relatedId 关联业务ID（可选）
     */
    public void send(Long userId, String title, String content, String type, Long relatedId) {
        // 1. 持久化到 notifications 表
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setIsRead(0);
        notification.setRelatedId(relatedId);
        notification.setCreatedAt(LocalDateTime.now());

        try {
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.error("通知持久化失败: userId={}, title={}, error={}", userId, title, e.getMessage());
        }

        // 2. WebSocket 实时推送（用户不在线时忽略，下次登录可从 DB 拉取）
        try {
            webSocketHandler.sendNotification(userId, title, content);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败（用户可能不在线）: userId={}, error={}", userId, e.getMessage());
        }

        log.info("通知已发送: userId={}, type={}, title={}", userId, type, title);
    }

    /**
     * 发送通知（简化版，无关联业务ID）
     */
    public void send(Long userId, String title, String content, String type) {
        send(userId, title, content, type, null);
    }
}

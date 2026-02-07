package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知控制器。
 * 设计意图：为其他微服务提供统一的消息发送入口，避免直接依赖 WebSocket 实现细节。
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationWebSocketHandler notificationHandler;

    /**
     * 发送通知给指定用户。
     * 逻辑说明：控制层只负责参数解析与异常兜底，具体推送由 WebSocket 处理器完成。
     */
    @PostMapping("/send")
    public Result<Void> sendNotification(@RequestBody Map<String, Object> request) {
        Long userId = Long.valueOf(request.get("userId").toString());
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        String type = request.get("type") != null ? (String) request.get("type") : "NOTIFICATION";

        log.info("发送通知: userId={}, title={}, type={}", userId, title, type);

        // 通过 WebSocket 发送实时通知
        notificationHandler.sendNotification(userId, title, content);

        return Result.success("通知发送成功", null);
    }

    /**
     * 批量发送通知。
     * 业务原因：后台运营类场景需要一次性推送给多个用户，便于公告触达与活动提醒。
     */
    @PostMapping("/send-batch")
    public Result<Map<String, Object>> sendBatchNotification(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<Long> userIds = (java.util.List<Long>) request.get("userIds");
        String title = (String) request.get("title");
        String content = (String) request.get("content");

        int successCount = 0;
        int failCount = 0;

        for (Object userIdObj : userIds) {
            try {
                Long userId = Long.valueOf(userIdObj.toString());
                notificationHandler.sendNotification(userId, title, content);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("发送通知给用户失败: {}", e.getMessage());
            }
        }

        Map<String, Object> result = Map.of(
                "successCount", successCount,
                "failCount", failCount,
                "total", userIds.size());

        return Result.success("批量通知发送完成", result);
    }

    /**
     * 检查用户是否在线。
     * 用于前端判断是否需要即时推送或进入离线消息队列。
     */
    @GetMapping("/online/{userId}")
    public Result<Boolean> isUserOnline(@PathVariable Long userId) {
        boolean online = notificationHandler.isUserOnline(userId);
        return Result.success(online);
    }
}

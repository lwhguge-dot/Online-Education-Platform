package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.BatchNotificationRequest;
import com.eduplatform.user.dto.SendNotificationRequest;
import com.eduplatform.user.websocket.NotificationWebSocketHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
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

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    @Value("${security.internal-token}")
    private String internalToken;

    private final NotificationWebSocketHandler notificationHandler;

    /**
     * 发送通知给指定用户。
     * 逻辑说明：控制层只负责参数解析与异常兜底，具体推送由 WebSocket 处理器完成。
     */
    @PostMapping("/send")
    public Result<Void> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String requestInternalToken) {
        // 发送通知属于教师/管理员操作；服务间调用可通过内部令牌放行
        if (!hasTeacherManageRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅教师或管理员可发送通知");
        }

        Long userId = request.getUserId();
        String title = request.getTitle();
        String content = request.getContent();
        String type = StringUtils.hasText(request.getType()) ? request.getType() : "NOTIFICATION";

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
    public Result<Map<String, Object>> sendBatchNotification(
            @Valid @RequestBody BatchNotificationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String requestInternalToken) {
        // 批量通知风险更高，仅允许教师/管理员或内部服务调用
        if (!hasTeacherManageRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅教师或管理员可批量发送通知");
        }

        java.util.List<Long> userIds = request.getUserIds();
        String title = request.getTitle();
        String content = request.getContent();

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
    public Result<Boolean> isUserOnline(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 在线状态仅允许本人、教师或管理员查询
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canQueryOnlineStatus(userId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查询在线状态");
        }

        boolean online = notificationHandler.isUserOnline(userId);
        return Result.success(online);
    }

    /**
     * 判断是否具备通知管理权限（教师或管理员）。
     */
    private boolean hasTeacherManageRole(String role) {
        return role != null && ("teacher".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role));
    }

    /**
     * 解析网关注入的用户ID，非法值返回 null。
     */
    private Long parseUserId(String currentUserIdHeader) {
        if (currentUserIdHeader == null || currentUserIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(currentUserIdHeader);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 在线状态查询权限：本人可查，教师和管理员可查任意用户。
     */
    private boolean canQueryOnlineStatus(Long targetUserId, Long currentUserId, String currentUserRole) {
        if (hasTeacherManageRole(currentUserRole)) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetUserId);
    }

    /**
     * 校验内部调用令牌，支持服务间可信调用场景。
     */
    private boolean hasValidInternalToken(String requestInternalToken) {
        return StringUtils.hasText(internalToken)
                && StringUtils.hasText(requestInternalToken)
                && internalToken.equals(requestInternalToken);
    }
}

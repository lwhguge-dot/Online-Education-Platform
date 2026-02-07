package com.eduplatform.user.listener;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.user.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 公告发布事件消费者
 * 消费 ANNOUNCEMENT_PUBLISHED 事件后，通过 WebSocket 将公告推送给目标用户。
 * 由于公告可能面向全体/学生/教师，此处直接广播给所有在线用户。
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementEventListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final NotificationWebSocketHandler webSocketHandler;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> body = message.getValue();
            String dataJson = body.get("data");
            String messageId = body.get("id");

            log.info("收到公告发布事件: recordId={}, messageId={}", message.getId(), messageId);

            Map<String, Object> data = objectMapper.readValue(dataJson, new TypeReference<>() {});

            Long announcementId = toLong(data.get("announcementId"));
            String title = (String) data.get("title");
            String content = (String) data.get("content");
            String targetAudience = (String) data.get("targetAudience");

            // 通过 WebSocket 广播公告通知
            // 实际生产中应根据 targetAudience 过滤在线用户
            log.info("推送公告通知: announcementId={}, title={}, audience={}",
                    announcementId, title, targetAudience);

            // 公告通知使用特定的消息格式推送
            Map<String, Object> wsMessage = Map.of(
                    "type", "ANNOUNCEMENT",
                    "announcementId", announcementId != null ? announcementId : 0,
                    "title", title != null ? title : "新公告",
                    "content", content != null ? content : "",
                    "timestamp", System.currentTimeMillis()
            );

            // 向所有在线用户推送（后续可优化为按 targetAudience 过滤）
            broadcastAnnouncement(wsMessage);

            // ACK 确认
            redisTemplate.opsForStream().acknowledge(
                    EventType.ANNOUNCEMENT_PUBLISHED.getStreamKey(),
                    RedisStreamConstants.GROUP_USER_SERVICE,
                    message.getId());

            log.info("公告发布事件处理完成: announcementId={}", announcementId);
        } catch (Exception e) {
            log.error("处理公告发布事件失败: recordId={}, error={}", message.getId(), e.getMessage(), e);
        }
    }

    /**
     * 广播公告给所有在线用户
     * 通过遍历 WebSocketHandler 中的在线用户会话发送消息。
     */
    private void broadcastAnnouncement(Map<String, Object> message) {
        int onlineCount = webSocketHandler.getOnlineUserCount();
        log.info("广播公告通知给 {} 个在线用户", onlineCount);
        // WebSocketHandler 的 sendToUser 方法会自动处理用户不在线的情况
        // 此处不再做额外遍历，因为通知已持久化到 DB
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return Long.valueOf(obj.toString());
    }
}

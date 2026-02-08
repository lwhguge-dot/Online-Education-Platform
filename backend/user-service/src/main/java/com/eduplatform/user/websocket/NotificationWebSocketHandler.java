package com.eduplatform.user.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    
    private static final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket 连接建立: userId={}", userId);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到消息: {}", payload);
        
        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            
            if ("REGISTER".equals(type)) {
                // 出于安全考虑，不再信任客户端上报的 userId。
                Long userId = getUserIdFromSession(session);
                if (userId != null) {
                    userSessions.put(userId, session);
                    log.info("用户确认 WebSocket 会话: userId={}", userId);
                    sendMessage(session, Map.of("type", "REGISTERED", "message", "连接成功"));
                } else {
                    sendMessage(session, Map.of("type", "ERROR", "message", "身份认证失败"));
                }
                return;
            }

            if ("PING".equals(type)) {
                sendMessage(session, Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
            }
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }
    
    public void sendToUser(Long userId, Map<String, Object> message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }
    
    public void sendForceLogout(Long userId, String reason) {
        Map<String, Object> message = Map.of(
            "type", "FORCE_LOGOUT",
            "reason", reason,
            "timestamp", System.currentTimeMillis()
        );
        sendToUser(userId, message);
    }
    
    public void sendNotification(Long userId, String title, String content) {
        Map<String, Object> message = Map.of(
            "type", "NOTIFICATION",
            "title", title,
            "content", content,
            "timestamp", System.currentTimeMillis()
        );
        sendToUser(userId, message);
    }
    
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送消息失败: {}", e.getMessage());
        }
    }
    
    private Long getUserIdFromSession(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            return Long.valueOf(userId.toString());
        }
        return null;
    }
    
    public boolean isUserOnline(Long userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    public int getOnlineUserCount() {
        return (int) userSessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}

package com.eduplatform.user.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eduplatform.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    
    private static final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService authTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket 连接建立: userId={}", userId);
            return;
        }

        authTimeoutExecutor.schedule(() -> {
            if (!session.isOpen()) {
                return;
            }
            Long currentUserId = getUserIdFromSession(session);
            if (currentUserId != null) {
                return;
            }
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到消息: {}", payload);
        
        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");
            
            if ("AUTH".equals(type)) {
                if (getUserIdFromSession(session) != null) {
                    sendMessage(session, Map.of("type", "AUTH_OK", "message", "已认证"));
                    return;
                }

                String token = msg.get("token") != null ? msg.get("token").toString() : null;
                if (token == null || token.isBlank() || !jwtUtil.validateToken(token)) {
                    sendMessage(session, Map.of("type", "AUTH_FAILED", "message", "身份认证失败"));
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }

                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId == null) {
                    sendMessage(session, Map.of("type", "AUTH_FAILED", "message", "身份认证失败"));
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }

                WebSocketSession oldSession = userSessions.put(userId, session);
                if (oldSession != null && oldSession.isOpen() && oldSession != session) {
                    try {
                        oldSession.close(CloseStatus.NORMAL);
                    } catch (IOException ignored) {
                    }
                }

                session.getAttributes().put("userId", userId);
                log.info("用户认证 WebSocket 会话: userId={}", userId);
                sendMessage(session, Map.of("type", "AUTH_OK", "message", "连接成功"));
                return;
            }

            if ("PING".equals(type)) {
                sendMessage(session, Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
                return;
            }

            if ("REGISTER".equals(type)) {
                sendMessage(session, Map.of("type", "ERROR", "message", "请先发送 AUTH 消息"));
                return;
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
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
            log.error("发送消息失败", e);
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

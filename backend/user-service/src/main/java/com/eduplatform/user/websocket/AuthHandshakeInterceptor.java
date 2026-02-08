package com.eduplatform.user.websocket;

import com.eduplatform.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 * 设计意图：
 * 1. 在握手阶段校验 JWT，阻断未授权连接。
 * 2. 将可信 userId 写入会话属性，供后续消息处理使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            return true;
        }

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("WebSocket 握手失败：token 无效");
                return false;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                log.warn("WebSocket 握手失败：token 中缺少 userId");
                return false;
            }

            attributes.put("userId", userId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 握手失败：token 解析异常，error={}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }
}

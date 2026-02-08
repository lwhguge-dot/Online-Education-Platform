package com.eduplatform.user.config;

import com.eduplatform.user.websocket.AuthHandshakeInterceptor;
import com.eduplatform.user.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final NotificationWebSocketHandler notificationHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Value("${websocket.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 支持通过配置声明允许的来源，避免生产环境放开所有 Origin
        String[] allowedOriginArray = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addHandler(notificationHandler, "/ws/notification")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins(allowedOriginArray);
    }
}

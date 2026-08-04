package com.hckcapital.be.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                // No cookie/session-based auth involved (JWT via query param, validated in
                // the interceptor above), and every platform this app ships to (iOS/Android
                // native WebSocket, no browser same-origin policy) needs this wide open —
                // same reasoning as every REST controller here already being reachable from
                // any origin.
                .setAllowedOrigins("*");
    }
}

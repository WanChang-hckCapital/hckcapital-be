package com.hckcapital.be.config;

import com.hckcapital.be.service.NotificationSocketRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** /ws/notifications — see JwtHandshakeInterceptor for how `profileId` lands in the session
 * attributes before this ever sees the connection, and NotificationSocketRegistry for what
 * actually happens with it. One-way (server → client) by design: nothing here handles an
 * incoming client message, since the RN side (useNotificationSocket.ts) only ever listens. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationSocketRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String profileId = (String) session.getAttributes().get(JwtHandshakeInterceptor.PROFILE_ID_ATTR);
        log.info("[ws] connection established sessionId={} profileId={}", session.getId(), profileId);
        if (profileId != null) {
            registry.register(profileId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String profileId = (String) session.getAttributes().get(JwtHandshakeInterceptor.PROFILE_ID_ATTR);
        log.info("[ws] connection closed sessionId={} profileId={} status={}", session.getId(), profileId, status);
        if (profileId != null) {
            registry.unregister(profileId, session);
        }
    }
}

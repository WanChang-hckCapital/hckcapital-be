package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One live WebSocket connection per profile (a fresh connection replaces whatever was
 * registered before, e.g. after a reconnect) — see NotificationWebSocketHandler for how
 * entries get added/removed, and NotificationService.createNotification for the one caller
 * that actually sends through this. This is the ground truth for "is this profile actually
 * looking at the app right now," strictly more accurate than RedisPresenceService's own
 * 5-minute-TTL heartbeat (which still exists for other presence uses, but no longer gates
 * the push-vs-live decision — see createNotification's own doc comment). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSocketRegistry {

    private final Map<String, WebSocketSession> sessionsByProfileId = new ConcurrentHashMap<>();
    // Spring Boot's own auto-configured bean — has the JavaTimeModule (LocalDateTime etc.)
    // registered already, same as whatever serializes every REST response in this app. A
    // bare `new ObjectMapper()` here doesn't, and NotificationResponse's own `createdAt`
    // (LocalDateTime) would throw InvalidDefinitionException on every single send — this
    // was caught silently by send()'s own try/catch, so the symptom was just "nothing ever
    // arrives over the socket," no visible error client-side.
    private final ObjectMapper objectMapper;

    public void register(String profileId, WebSocketSession session) {
        sessionsByProfileId.put(profileId, session);
        log.info("[ws] registry now has {} live session(s): {}", sessionsByProfileId.size(), sessionsByProfileId.keySet());
    }

    public void unregister(String profileId, WebSocketSession session) {
        // Only remove if it's still this exact session — a fast reconnect could otherwise
        // have already registered a newer one under the same profileId by the time this
        // (the *old* connection's) close event is processed.
        sessionsByProfileId.remove(profileId, session);
        log.info("[ws] registry now has {} live session(s): {}", sessionsByProfileId.size(), sessionsByProfileId.keySet());
    }

    /** Returns true if a live session existed and the send succeeded — the caller
     * (NotificationService) uses this to decide whether a push notification is still worth
     * attempting as a fallback. */
    public boolean send(String profileId, Object payload) {
        WebSocketSession session = sessionsByProfileId.get(profileId);
        log.info("[ws] send() targetProfileId={} sessionFound={} currentRegistry={}",
                profileId, session != null, sessionsByProfileId.keySet());
        if (session == null || !session.isOpen()) return false;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (Exception e) {
            log.warn("Failed to send live notification over WebSocket (profileId={})", profileId, e);
            return false;
        }
    }
}

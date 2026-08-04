package com.hckcapital.be.config;

import com.hckcapital.be.service.CardService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/** Authenticates the /ws/notifications handshake — same JWT this app already issues on
 * login, just carried as a `?token=` query param instead of an Authorization header (see
 * SecurityConfig's own doc comment on why: a plain WebSocket client can't reliably set
 * custom handshake headers). Resolves straight through to a profileId (same
 * CardService.resolveActiveProfileId every REST endpoint uses) and stores it in the
 * session attributes, where NotificationWebSocketHandler reads it back on connect — nothing
 * downstream of this needs to touch the JWT again. Rejects the handshake outright (`false`)
 * on a missing/invalid/expired token, same as a REST call would 403. */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PROFILE_ID_ATTR = "profileId";

    private final JwtUtil jwtUtil;
    private final CardService cardService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes
    ) {
        List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");
        String token = tokenParams != null && !tokenParams.isEmpty() ? tokenParams.get(0) : null;

        if (token == null || !jwtUtil.isValid(token)) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String memberId = jwtUtil.extractMemberId(token);
            ObjectId profileId = cardService.resolveActiveProfileId(memberId);
            attributes.put(PROFILE_ID_ATTR, profileId.toHexString());
            return true;
        } catch (Exception e) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception
    ) {
    }
}

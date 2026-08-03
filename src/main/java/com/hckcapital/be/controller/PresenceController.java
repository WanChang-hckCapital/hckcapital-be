package com.hckcapital.be.controller;

import com.hckcapital.be.service.CardService;
import com.hckcapital.be.service.RedisPresenceService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Online-presence heartbeat — see RedisPresenceService. The RN app calls /online on
 * foreground and on an interval while foregrounded (the 5-minute TTL means a missed
 * heartbeat — app backgrounded/killed — naturally expires back to "offline" without needing
 * an explicit /offline call on every background transition; that endpoint exists mainly for
 * a clean sign-out). Ported from the old Next.js reference's own app/api/v1/redis/
 * {online,offline} routes. */
@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final CardService cardService;
    private final RedisPresenceService redisPresenceService;

    @PostMapping("/online")
    public ResponseEntity<Void> markOnline(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        redisPresenceService.markOnline(profileId.toHexString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/offline")
    public ResponseEntity<Void> markOffline(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        redisPresenceService.markOffline(profileId.toHexString());
        return ResponseEntity.noContent().build();
    }
}

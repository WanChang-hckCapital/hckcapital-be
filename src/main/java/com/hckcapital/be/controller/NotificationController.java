package com.hckcapital.be.controller;

import com.hckcapital.be.dto.NotificationPageResponse;
import com.hckcapital.be.dto.UnreadCountResponse;
import com.hckcapital.be.service.CardService;
import com.hckcapital.be.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** NotificationScreen.tsx — see NotificationService for the port of the old Next.js
 * reference's own getUserNotification/createNotification. Every endpoint here resolves the
 * caller's own active profile from the JWT via CardService.resolveActiveProfileId (same
 * memberId-from-Authentication pattern as ProfileController's own notification-settings
 * endpoints — done here rather than inside NotificationService itself, since CardService
 * needs to call back into NotificationService to fire like/comment triggers, and Spring
 * beans can't depend on each other in a cycle) — there's no "view someone else's
 * notifications" case. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CardService cardService;

    @GetMapping
    public ResponseEntity<NotificationPageResponse> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String profileId = resolveProfileId(authentication);
        return ResponseEntity.ok(notificationService.getUserNotifications(profileId, page, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(Authentication authentication) {
        String profileId = resolveProfileId(authentication);
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.getUnreadCount(profileId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(Authentication authentication, @PathVariable String notificationId) {
        String profileId = resolveProfileId(authentication);
        notificationService.markAsRead(notificationId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        String profileId = resolveProfileId(authentication);
        notificationService.markAllAsRead(profileId);
        return ResponseEntity.noContent().build();
    }

    private String resolveProfileId(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return profileId.toHexString();
    }
}

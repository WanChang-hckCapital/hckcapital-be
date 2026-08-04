package com.hckcapital.be.service;

import com.hckcapital.be.dto.NotificationPageResponse;
import com.hckcapital.be.dto.NotificationResponse;
import com.hckcapital.be.model.Notification;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.repository.NotificationRepository;
import com.hckcapital.be.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** NotificationScreen.tsx's own feed — ported from the old Next.js reference's own
 * lib/models/notification.ts + lib/actions/notification.actions.ts (createNotification/
 * getUserNotification), but deliberately smaller in two ways:
 *
 * 1. Only a handful of real trigger types are wired (see CardService.toggleLike/
 *    CardCommentService's own comment-add / ProfileService's own follow — search this
 *    codebase for calls into createNotification) rather than porting all ~20 of the
 *    reference's own notify* template functions. The durable Notification document +
 *    feed/read-state infra here is identical regardless of how many trigger call sites use
 *    it — adding a new event type later is just one more createNotification call, no schema
 *    change.
 * 2. No email-digest batching. The reference's own Redis-backed queue existed to bundle up
 *    to N missed notifications into a single email; this port skips that in favor of two
 *    real delivery paths, tried in order:
 *      a. Live, over NotificationSocketRegistry — if the recipient has an open
 *         /ws/notifications connection (i.e. the app is actually open right now), they get
 *         the notification the instant it's created, and useNotificationSocket.ts on the RN
 *         side shows it as a local notification banner immediately (same
 *         scheduleNotificationAsync call the LandingScreen test button uses).
 *      b. Otherwise, a real OS-level push via ExpoPushService, for a closed/backgrounded
 *         app.
 *    This is a strictly more accurate signal than the old RedisPresenceService-only check
 *    used to be — a live socket connection *is* "online," rather than an approximation of
 *    it from a 5-minute heartbeat TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ProfileRepository profileRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisPresenceService redisPresenceService;
    private final ExpoPushService expoPushService;
    private final NotificationSocketRegistry notificationSocketRegistry;

    /** The one write path for every notification — mirrors the reference's own
     * createNotification ("the only function that used to insert into the db"). No-ops on
     * a self-notification (receiver == sender) — every reference notify* trigger guards
     * this individually at its own call site; centralizing it here means every future
     * trigger gets it for free. */
    public void createNotification(
            ObjectId receiverProfileId, ObjectId senderProfileId, String notificationType,
            ObjectId targetId, String targetType, Map<String, Object> relatedData
    ) {
        log.info("[notify] createNotification type={} receiver={} sender={}", notificationType, receiverProfileId, senderProfileId);
        if (receiverProfileId == null) return;
        if (receiverProfileId.equals(senderProfileId)) return;

        Notification notification = new Notification();
        notification.setReceiverUserId(receiverProfileId);
        notification.setSenderUserId(senderProfileId);
        notification.setNotificationType(notificationType);
        notification.setTargetId(targetId);
        notification.setTargetType(targetType);
        notification.setRelatedData(relatedData);
        notification.setRead(false);
        notificationRepository.save(notification);

        // Recompute from Mongo rather than blindly INCR the cache — this backend shares its
        // `notifications` collection with the old Next.js reference app, so a profile can
        // already have unread notifications the Redis cache was never primed with (an INCR
        // against a cold/missing key would silently undercount everything that predates this
        // service). Cheap enough to just always get it right.
        String receiverId = receiverProfileId.toHexString();
        redisPresenceService.setUnreadCount(receiverId, notificationRepository.countByReceiverUserIdAndReadFalse(receiverProfileId));

        Profile sender = senderProfileId != null
                ? profileRepository.findById(senderProfileId.toHexString()).orElse(null) : null;

        NotificationResponse liveEvent = new NotificationResponse(
                notification.getId(),
                senderProfileId != null ? senderProfileId.toHexString() : null,
                sender != null ? sender.getAccountname() : null,
                sender != null ? sender.getImageFilePath() : null,
                notificationType,
                false,
                targetId != null ? targetId.toHexString() : null,
                targetType,
                relatedData,
                notification.getCreatedAt()
        );

        boolean deliveredLive = notificationSocketRegistry.send(receiverId, liveEvent);
        if (!deliveredLive) {
            sendPush(receiverProfileId, sender, notificationType, relatedData);
        }
    }

    private void sendPush(ObjectId receiverProfileId, Profile sender, String notificationType, Map<String, Object> relatedData) {
        Profile receiver = profileRepository.findById(receiverProfileId.toHexString()).orElse(null);
        if (receiver == null || receiver.getExpoPushToken() == null || receiver.getExpoPushToken().isBlank()) return;

        String senderName = sender != null && sender.getAccountname() != null ? sender.getAccountname() : "Someone";

        String body = switch (notificationType) {
            case "CARD_LIKED" -> senderName + " liked your card"
                    + cardTitleSuffix(relatedData);
            case "CARD_COMMENTED" -> senderName + " commented on your card"
                    + cardTitleSuffix(relatedData);
            case "PROFILE_FOLLOWED" -> senderName + " started following you";
            default -> senderName + " sent you a notification";
        };

        Map<String, Object> data = new HashMap<>();
        data.put("notificationType", notificationType);
        expoPushService.send(receiver.getExpoPushToken(), "flxBubble", body, data);
    }

    private String cardTitleSuffix(Map<String, Object> relatedData) {
        Object title = relatedData != null ? relatedData.get("cardTitle") : null;
        return title != null ? ": \"" + title + "\"" : "";
    }

    /** Newest-first, sender info resolved in one batched lookup rather than N+1 — see
     * NotificationResponse's own doc comment on why the sender's name/image are baked into
     * each row rather than left for the RN app to fetch separately. */
    public NotificationPageResponse getUserNotifications(String profileId, int page, int limit) {
        ObjectId receiverId = new ObjectId(profileId);
        List<Notification> notifications = notificationRepository.findByReceiverUserIdOrderByCreatedAtDesc(
                receiverId, PageRequest.of(Math.max(0, page - 1), limit));

        Set<String> senderIds = notifications.stream()
                .map(Notification::getSenderUserId)
                .filter(Objects::nonNull)
                .map(ObjectId::toHexString)
                .collect(Collectors.toSet());
        Map<String, Profile> sendersById = senderIds.isEmpty() ? Map.of() :
                profileRepository.findAllById(senderIds).stream()
                        .collect(Collectors.toMap(Profile::getId, p -> p));

        List<NotificationResponse> responses = notifications.stream().map(n -> {
            Profile sender = n.getSenderUserId() != null ? sendersById.get(n.getSenderUserId().toHexString()) : null;
            return new NotificationResponse(
                    n.getId(),
                    n.getSenderUserId() != null ? n.getSenderUserId().toHexString() : null,
                    sender != null ? sender.getAccountname() : null,
                    sender != null ? sender.getImageFilePath() : null,
                    n.getNotificationType(),
                    n.isRead(),
                    n.getTargetId() != null ? n.getTargetId().toHexString() : null,
                    n.getTargetType(),
                    n.getRelatedData(),
                    n.getCreatedAt()
            );
        }).collect(Collectors.toList());

        return new NotificationPageResponse(responses, notifications.size() == limit);
    }

    public void markAsRead(String notificationId, String profileId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getReceiverUserId() == null
                || !notification.getReceiverUserId().toHexString().equals(profileId) || notification.isRead()) {
            return;
        }
        notification.setRead(true);
        notificationRepository.save(notification);

        Long cached = redisPresenceService.getUnreadCount(profileId);
        if (cached != null) redisPresenceService.setUnreadCount(profileId, cached - 1);
    }

    public void markAllAsRead(String profileId) {
        Query query = Query.query(Criteria.where("receiverUserId").is(new ObjectId(profileId)).and("read").is(false));
        mongoTemplate.updateMulti(query, new Update().set("read", true), Notification.class);
        redisPresenceService.setUnreadCount(profileId, 0);
    }

    /** Redis-cached — a cache miss falls back to a real Mongo count and reprimes the cache,
     * see RedisPresenceService's own doc comment. */
    public long getUnreadCount(String profileId) {
        Long cached = redisPresenceService.getUnreadCount(profileId);
        if (cached != null) return cached;
        long count = notificationRepository.countByReceiverUserIdAndReadFalse(new ObjectId(profileId));
        redisPresenceService.setUnreadCount(profileId, count);
        return count;
    }
}

package com.hckcapital.be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Notifications > online presence + unread-count cache — ported from the old Next.js
 * reference's own app/api/v1/redis/{online,offline,notification-count} routes (lib/redis/
 * redis.ts, plain ioredis). Two independent concerns share this one Redis instance:
 *
 * 1. Online presence (`online:profile:{id}`, 5-minute TTL, same as the reference) — a
 *    heartbeat key the RN app refreshes while foregrounded (see PresenceController). Read
 *    by NotificationService.createNotification to decide whether a push notification is
 *    actually worth sending — no point pushing to a phone that's already looking at the
 *    app.
 * 2. Unread-count cache (`notifications:unread:{id}`) — a plain integer, not the
 *    reference's own per-user list-of-full-notifications structure (that list existed
 *    there to batch up to N notifications before firing an email digest, a feature this
 *    port doesn't carry over — see NotificationService's own doc comment). Kept in sync by
 *    NotificationService on every write (create/markRead/markAllRead) rather than given a
 *    TTL, since it's cheap to keep correct and a stale/missing entry just means one extra
 *    Mongo count query to reprime it.
 *
 * Every method below swallows its own Redis errors rather than letting them propagate —
 * Redis here is a best-effort cache/presence layer, not a system of record (Mongo already
 * is, for both concerns), so it must never be able to break anything else. Before this was
 * added, a Redis outage surfaced as an uncaught exception from these calls, which this
 * app's own exception handling turned into a 403 — indistinguishable, client-side, from a
 * genuinely expired JWT (see apiClient.ts's authFetch on the RN side), so login itself
 * looked broken even though the actual /auth/login call never touches Redis at all. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPresenceService {

    private static final Duration ONLINE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    private String onlineKey(String profileId) {
        return "online:profile:" + profileId;
    }

    private String unreadCountKey(String profileId) {
        return "notifications:unread:" + profileId;
    }

    public void markOnline(String profileId) {
        try {
            redisTemplate.opsForValue().set(onlineKey(profileId), "1", ONLINE_TTL);
        } catch (Exception e) {
            log.warn("Redis markOnline failed (profileId={})", profileId, e);
        }
    }

    public void markOffline(String profileId) {
        try {
            redisTemplate.delete(onlineKey(profileId));
        } catch (Exception e) {
            log.warn("Redis markOffline failed (profileId={})", profileId, e);
        }
    }

    /** Defaults to "not online" when Redis itself is unreachable — the only consequence is
     * NotificationService.createNotification attempting a push it might not have needed to
     * (ExpoPushService is itself best-effort and swallows its own failures), never a hard
     * error. */
    public boolean isOnline(String profileId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(onlineKey(profileId)));
        } catch (Exception e) {
            log.warn("Redis isOnline failed (profileId={})", profileId, e);
            return false;
        }
    }

    /** Null means "not cached" — the caller should fall back to a real Mongo count and
     * reprime via setUnreadCount. A Redis failure is treated the same as a cache miss, for
     * the same reason. */
    public Long getUnreadCount(String profileId) {
        try {
            String value = redisTemplate.opsForValue().get(unreadCountKey(profileId));
            return value != null ? Long.parseLong(value) : null;
        } catch (Exception e) {
            log.warn("Redis getUnreadCount failed (profileId={})", profileId, e);
            return null;
        }
    }

    public void setUnreadCount(String profileId, long count) {
        try {
            redisTemplate.opsForValue().set(unreadCountKey(profileId), String.valueOf(Math.max(0, count)));
        } catch (Exception e) {
            log.warn("Redis setUnreadCount failed (profileId={})", profileId, e);
        }
    }
}

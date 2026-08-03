package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/** Ported from the old Next.js reference's own lib/models/notification.ts — collection
 * name verified via Mongoose's own pluralization ("notifications"), same as every other
 * shared collection in this backend. `notificationType`/`targetType` are plain strings
 * (not a Java enum) for the same reason the reference itself never enforced its own enum
 * server-side (see notification.ts's commented-out `enum:` lines) — new event types can be
 * added on either side without a schema migration. `relatedData` is a free-form bag (the
 * reference's own `Schema.Types.Mixed`) — whatever per-type display info the RN feed needs
 * (e.g. a comment snippet, a card title) that isn't already derivable from targetId/Type. */
@Data
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private ObjectId receiverUserId;

    private ObjectId senderUserId;

    private String notificationType;

    private boolean read = false;

    private ObjectId targetId;

    private String targetType;

    private Map<String, Object> relatedData;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

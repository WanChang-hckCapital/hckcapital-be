package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** Mirrors the old Next.js reference project's own lib/models/points-log.ts field-for-field
 * — same MongoDB collection ("pointslogs", Mongoose's default pluralized name), the audit
 * trail behind every Profile.bubblePoint change (see ProfileService.completeOnboarding's own
 * referral-reward payout, the only writer of this collection in this backend so far).
 * mission/luckyDraw/dayPassSubscription/redeemGift refs are kept for schema parity with the
 * shared database (those sourceTypes already exist from the reference app's own writes —
 * "mission", "day_pass_subscription", etc.) but nothing in this backend ever sets them;
 * only `sourceType: "referral"` is written here. */
@Data
@Document(collection = "pointslogs")
public class PointsLog {
    @Id
    private String id;

    private ObjectId profileId;

    private Integer pointChanges;

    private Integer beforePoints;

    private Integer afterPoints;

    private String sourceType;

    private ObjectId referral;

    private ObjectId mission;

    private ObjectId luckyDraw;

    private ObjectId dayPassSubscription;

    private ObjectId redeemGift;

    private String description;

    private Date createdAt;

    private Date updatedAt;
}

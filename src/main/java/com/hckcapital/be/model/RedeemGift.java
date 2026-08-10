package com.hckcapital.be.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Mirrors the old Next.js reference project's own lib/models/redeem-gift.ts field-for-field
 * — same MongoDB collection ("redeemgifts", Mongoose's default pluralized name). Unlike
 * missions (a hardcoded config, see MissionService.MISSION_CONFIG), gifts are real Mongo
 * documents the admin creates/edits — this backend only reads them (RedeemService.listGifts)
 * and decrements `stock`/spends `pointsRequired` on redemption (RedeemService.redeem); no
 * admin CRUD for gifts is ported here yet, same "read what already exists in the shared
 * database" scope as ReferralHistory/PointsLog. */
@Document(collection = "redeemgifts")
@Data
public class RedeemGift {
    @Id
    private String id;

    // "platform" | "coupon" | "blindbox" | "grand" in the reference's own schema enum — kept
    // as a plain String (not a Java enum) since only "platform" (VIP-days grants) is ever
    // acted on specially here; every other category is just displayed.
    private String category;

    private String name;

    private Double marketValue;

    private Integer pointsRequired;

    private Integer daysRequiredText;

    // Only meaningful for category "platform" — how many VIP days redeeming this grants
    // (see RedeemService.redeem's own Subscription-creation logic).
    private Integer subscriptionDays;

    private String description;

    // "hot" | "grand_prize" | "limited" | "none" in the reference's own schema enum.
    private String tag;

    private Boolean isHot = false;

    private Integer stock = 999;

    private Boolean isActive = true;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One row of GET /api/v1/profile/points — mirrors the old Next.js reference project's own
 * loadPersonalPoints serialized log shape (lib/actions/user.actions.ts) field-for-field,
 * including the same denormalized display fields (missionType/missionPeriod/
 * redeemGiftName/referralCode/referrerName/refereeName) resolved server-side here via
 * ProfileService.getPointsHistory's own lookups, rather than the reference's Mongoose
 * `.populate()` chain — same end result (the RN client never needs a second round trip to
 * label a log entry), just resolved with explicit repository finds instead of populate. */
@Data
@AllArgsConstructor
public class PointsLogEntryResponse {
    private String id;
    private int pointChanges;
    private int beforePoints;
    private int afterPoints;
    private String sourceType;
    private String description;
    private String createdAt;
    private String missionType;
    private String missionPeriod;
    private String redeemGiftName;
    private String referralCode;
    private String referrerName;
    private String refereeName;
}

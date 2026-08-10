package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** Mirrors the old Next.js reference project's own lib/models/referral-history.ts field-for-
 * field — same MongoDB collection ("referralhistories", Mongoose's default pluralized name),
 * so this reads the same referral records the reference app's own signup/onboarding flow
 * writes (referrer/referee linking, reward payout on onboarding completion — see that
 * project's updateMemberDetails). Only read from this backend so far (ReferralResponse via
 * ProfileService.getReferralInfo) — this backend's own signup flow doesn't yet write to this
 * collection (see AuthService.signup's own doc comment on Profile.referralCode).
 *
 * `status` is one of "uncompleted" | "completed" | "invalid" (the reference's own
 * ReferralStatus enum) — kept as a plain String rather than a Java enum since "invalid" is
 * declared there but never actually assigned by any code in that reference app, and this
 * backend only ever needs to filter on "completed". */
@Data
@Document(collection = "referralhistories")
public class ReferralHistory {
    @Id
    private String id;

    private ObjectId referrerId;

    private ObjectId refereeId;

    private String referralCode;

    private Integer rewardPoints;

    private String status;

    private Date createdAt;

    private Date updatedAt;
}

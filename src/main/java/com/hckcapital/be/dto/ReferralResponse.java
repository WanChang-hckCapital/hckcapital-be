package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/profile/referral — see ProfileService.getReferralInfo. Mirrors the old
 * Next.js reference project's own fetchUserReferralCode + getSuccessfulReferralCount (lib/
 * actions/user.actions.ts): the caller's own referral code plus how many of their referrals
 * have completed onboarding (ReferralHistory.status == "completed"). The shareable referral
 * link itself is built client-side from `referralCode` (see ReferralScreen.tsx), same as the
 * reference's own ReferralComponent.tsx — nothing web-specific to resolve server-side. */
@Data
@AllArgsConstructor
public class ReferralResponse {
    private String referralCode;
    private int successfulReferralsCount;
}

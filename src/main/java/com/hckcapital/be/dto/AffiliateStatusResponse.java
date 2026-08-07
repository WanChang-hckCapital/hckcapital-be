package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/profile/affiliate-status — see RewardfulService.getAffiliateStatus. Distinct
 * from AffiliatesOverviewResponse (the admin-only report of every affiliate): this checks
 * whether the *calling* profile is itself already one, by matching its own email against
 * Rewardful's affiliate list. Same configured/errorMessage gate as AffiliatesOverviewResponse
 * — `configured: false` means REWARDFUL_API_SECRET isn't set at all. `affiliate` is null
 * whenever `existingAffiliate` is false.
 *
 * Named `existingAffiliate`, not `isAffiliate` — Lombok generates `isAffiliate()` as the
 * getter for a boolean field already named `isAffiliate`, and Jackson strips that same `is`
 * prefix when deriving the JSON key, producing `"affiliate"` — colliding with this class's
 * own `affiliate` field (whose getter is `getAffiliate()`, also `"affiliate"`). Two fields
 * serializing to the same JSON key means one silently overwrites the other; the boolean lost
 * every time, so `isAffiliate` never actually appeared in the response at all. */
@Data
@AllArgsConstructor
public class AffiliateStatusResponse {
    private boolean configured;
    private String errorMessage;
    private boolean existingAffiliate;
    private RewardfulAffiliateResponse affiliate;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Dashboard > Affiliates — see RewardfulService.getAffiliatesOverview. `configured` is
 * false when REWARDFUL_API_SECRET isn't set at all, distinct from "configured but zero
 * campaigns/affiliates" (a real, empty result) — the RN screen shows a different message
 * for each rather than treating both as the same blank state. `errorMessage` is set only
 * when `configured` is true but the actual Rewardful API call still failed (bad key,
 * network issue, etc). */
@Data
@AllArgsConstructor
public class AffiliatesOverviewResponse {
    private boolean configured;
    private String errorMessage;
    private String campaignId;
    private List<RewardfulAffiliateResponse> affiliates;
}

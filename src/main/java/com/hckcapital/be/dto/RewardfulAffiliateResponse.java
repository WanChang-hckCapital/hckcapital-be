package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Mirrors the old Next.js reference's own dashboard/affiliates page — one card per
 * affiliate, showing name/id/email, visitor/lead/conversion counts, and an active/inactive
 * state badge. See RewardfulService for how this gets parsed out of Rewardful's raw
 * (snake_case) JSON. */
@Data
@AllArgsConstructor
public class RewardfulAffiliateResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String state;
    private int visitors;
    private int leads;
    private int conversions;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Mirrors the old Next.js reference's own fetchAllPromotionCodes coupon shape — see
 * StripeService.getPromotionsOverview. `amountOff` is in the smallest currency unit
 * (cents), same convention as StripePriceResponse. */
@Data
@AllArgsConstructor
public class StripeCouponResponse {
    private String id;
    private String name;
    private Long percentOff;
    private Long amountOff;
    private String currency;
    private String duration;
    private Long durationInMonths;
    private Long timesRedeemed;
    private Long maxRedemptions;
}

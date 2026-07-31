package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One Stripe promotion code — see StripeService.getPromotionsOverview. */
@Data
@AllArgsConstructor
public class StripePromotionCodeResponse {
    private String id;
    private String code;
    private boolean active;
    private StripeCouponResponse coupon;
}

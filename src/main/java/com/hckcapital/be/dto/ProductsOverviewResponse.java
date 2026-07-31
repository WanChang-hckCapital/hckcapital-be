package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Dashboard > Product — see StripeService.getProductsOverview. Same
 * configured/errorMessage distinction as AffiliatesOverviewResponse: `configured=false`
 * means STRIPE_SECRET_KEY isn't set at all (no API call attempted); `errorMessage` means
 * the key exists but the Stripe call itself failed. `showFreePlan` mirrors the old Next.js
 * reference's own shouldShowFreePlan() (FlxBubbleConstant key "BOOLEAN_SHOW_FREE_PLAN",
 * defaulting to true when unset) — when true, the RN app renders a $0 "Free" card using
 * the first product's own free-tier feature list, same as the reference's page.tsx. */
@Data
@AllArgsConstructor
public class ProductsOverviewResponse {
    private boolean configured;
    private String errorMessage;
    private boolean showFreePlan;
    private List<StripeProductResponse> products;
}

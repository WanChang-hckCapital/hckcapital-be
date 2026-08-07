package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/subscriptions/status — see SubscriptionService.getStatus. `currentProductId`
 * is the live Stripe Product id the caller's active subscription (if any) is on, matching
 * one of ProductsOverviewResponse's own StripeProductResponse.id values — the RN app joins
 * the two client-side to know which plan card to highlight, same as the old Next.js
 * reference's own ProductList.tsx (isCurrentPlan = hasActiveSubscription && product.id ===
 * currentProductId). Null/false fields all mean "no active subscription" (i.e. Free plan). */
@Data
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private boolean hasActiveSubscription;
    private String currentProductId;
    private boolean cancelAtPeriodEnd;
    // ISO-8601 instant, or null if unavailable/not applicable.
    private String currentPeriodEnd;
    private String stripeSubscriptionId;
}

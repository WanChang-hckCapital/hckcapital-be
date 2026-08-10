package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/redeem/vip-status — see RedeemService.getVipStatus. Deliberately separate
 * from SubscriptionService.getStatus (SubscriptionScreen.tsx's own Stripe-plan status,
 * which does a live Stripe lookup keyed on profile.stripeCustomerId): a points-redeemed VIP
 * grant is a local-only Subscription document with no Stripe subscription behind it at all,
 * so Stripe's own API would never know about it — this checks the local `subscriptions`
 * collection directly instead, mirroring the reference's own checkUserInSubscription. */
@Data
@AllArgsConstructor
public class VipStatusResponse {
    private boolean active;
    private String endDate;
    private int daysRemaining;
}

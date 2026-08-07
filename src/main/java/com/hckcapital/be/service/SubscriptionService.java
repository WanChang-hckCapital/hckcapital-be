package com.hckcapital.be.service;

import com.hckcapital.be.dto.CheckoutSessionResponse;
import com.hckcapital.be.dto.SubscriptionActionResponse;
import com.hckcapital.be.dto.SubscriptionStatusResponse;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.Subscription;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.SubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionCollection;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** User-facing subscribe/cancel/resume flow — ported from the old Next.js reference
 * project's own CheckoutForm.tsx/ProductList.tsx handlers and their backing
 * lib/actions/stripe.actions.ts functions (storeSubscription/checkOutSession/
 * updateSubscription/stopSubscriptionPlan/resumeSubscriptionPlan). Distinct from
 * StripeService (read-only admin-dashboard reporting) — this is the mutation-heavy,
 * real-user-money side, kept in its own service rather than bloating that one further.
 *
 * Deliberately excludes two things the reference has:
 * 1. Rewardful referral/promo-code wiring (`window.rewardful` is a browser-only global with
 *    no mobile equivalent — this app has no substitute attribution mechanism yet, so a
 *    mobile subscription is never attributed to an affiliate the way a web one can be).
 * 2. "Upcoming invoice" preview (Stripe renamed/reshaped that API since the reference was
 *    built — `Invoice.retrieveUpcoming` no longer exists on this SDK version, replaced by a
 *    differently-shaped `Invoice.createPreview`; a real replacement is a separate piece of
 *    work, not essential to subscribe/cancel/resume itself).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    // Where Stripe redirects back after a completed/cancelled Checkout — this app's own
    // custom URL scheme (see app.json's `scheme: "flxbubble"`), not a web page like the
    // reference's own NEXT_PUBLIC_BASE_URL/checkout/success route. useSubscription.ts on the
    // RN side opens the Checkout URL via expo-web-browser's openAuthSessionAsync, which
    // resolves once the in-app browser navigates to a URL starting with this prefix.
    private static final String CHECKOUT_REDIRECT_PREFIX = "flxbubble://checkout";

    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;

    /** Creates a local, "pending" Subscription record (same bookkeeping-first-then-confirm
     * shape as the reference's own storeSubscription, called right before its own checkout-
     * session POST), then a real Stripe Checkout Session for it. Unlike the reference, which
     * trusts client-supplied `totalAmount`/`paidTerms`, this looks the Price up on Stripe
     * itself server-side — no reason to trust a client-supplied amount for something that
     * charges real money. */
    public CheckoutSessionResponse createCheckoutSession(String profileId, String priceId) {
        if (secretKey == null || secretKey.isBlank()) {
            return new CheckoutSessionResponse(false, null, null, "Stripe is not configured.");
        }
        if (priceId == null || priceId.isBlank()) {
            return new CheckoutSessionResponse(false, null, null, "Missing priceId.");
        }
        try {
            Stripe.apiKey = secretKey;

            Profile profile = profileRepository.findById(profileId).orElse(null);
            if (profile == null) {
                return new CheckoutSessionResponse(false, null, null, "Profile not found.");
            }

            Price price = Price.retrieve(priceId);
            long unitAmount = price.getUnitAmount() != null ? price.getUnitAmount() : 0;
            int paidTerms = price.getRecurring() != null && "year".equals(price.getRecurring().getInterval()) ? 12 : 1;

            Subscription subscription = new Subscription();
            subscription.setStatus("pending");
            subscription.setPlanStarted(LocalDateTime.now());
            subscription.setEstimatedEndDate(LocalDateTime.now().plusMonths(paidTerms));
            subscription.setPaidTerms(paidTerms);
            subscription.setTotalAmount(unitAmount / 100.0);
            subscriptionRepository.save(subscription);

            profile.getSubscription().add(new ObjectId(subscription.getId()));
            profileRepository.save(profile);

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .setSuccessUrl(CHECKOUT_REDIRECT_PREFIX + "/success?session_id={CHECKOUT_SESSION_ID}&subscriptionId=" + subscription.getId())
                    .setCancelUrl(CHECKOUT_REDIRECT_PREFIX + "/cancel");
            if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
                paramsBuilder.setCustomerEmail(profile.getEmail());
            }

            Session session = Session.create(paramsBuilder.build());
            return new CheckoutSessionResponse(true, session.getUrl(), subscription.getId(), null);
        } catch (Exception e) {
            log.warn("Failed to create checkout session (profileId={})", profileId, e);
            return new CheckoutSessionResponse(false, null, null, e.getMessage());
        }
    }

    /** Called once useSubscription.ts's WebBrowser flow returns with a session_id — mirrors
     * the reference's own checkout/success/page.tsx (checkOutSession + updateSubscription):
     * retrieves the Checkout Session (expanding its resulting subscription), then persists
     * the resulting Stripe customer/subscription ids onto the local Profile/Subscription
     * records and marks the local Subscription "active".
     *
     * `subscriptionId` must already be in the caller's own profile.subscription list (i.e.
     * created by that same profile's own earlier createCheckoutSession call) — refusing
     * otherwise stops one profile from confirming a stray sessionId/subscriptionId pair
     * against a *different* profile's own pending Subscription record. */
    public SubscriptionActionResponse confirmCheckout(String profileId, String sessionId, String subscriptionId) {
        if (secretKey == null || secretKey.isBlank()) {
            return new SubscriptionActionResponse(false, "Stripe is not configured.");
        }
        try {
            Profile profile = profileRepository.findById(profileId).orElse(null);
            if (profile == null) {
                return new SubscriptionActionResponse(false, "Profile not found.");
            }

            Subscription subscription = null;
            if (subscriptionId != null && !subscriptionId.isBlank()) {
                boolean ownsSubscription = profile.getSubscription().stream()
                        .anyMatch(id -> id.toHexString().equals(subscriptionId));
                if (!ownsSubscription) {
                    return new SubscriptionActionResponse(false, "Subscription does not belong to this profile.");
                }
                subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
            }

            Stripe.apiKey = secretKey;
            SessionRetrieveParams retrieveParams = SessionRetrieveParams.builder()
                    .addExpand("subscription").addExpand("customer").build();
            Session checkoutSession = Session.retrieve(sessionId, retrieveParams, null);

            profile.setStripeCustomerId(checkoutSession.getCustomer());
            profileRepository.save(profile);

            if (subscription != null) {
                subscription.setStatus("active");
                subscription.setStripeSubscriptionId(checkoutSession.getSubscription());
                subscriptionRepository.save(subscription);
            }

            return new SubscriptionActionResponse(true, "Subscription confirmed.");
        } catch (Exception e) {
            log.warn("Failed to confirm checkout (profileId={}, sessionId={})", profileId, sessionId, e);
            return new SubscriptionActionResponse(false, e.getMessage());
        }
    }

    /** Live Stripe lookup, same as the reference's own fetchCurrentUserStripeCustomerId +
     * fetchSubscriptionStatus — the local Subscription record is bookkeeping, not the
     * source of truth for "is this profile actually subscribed right now" (a cancellation
     * made directly in Stripe's own dashboard, for instance, wouldn't touch it). No Stripe
     * customer yet, or no active/trialing subscription found, both mean "on the Free plan"
     * (hasActiveSubscription: false) rather than an error. */
    public SubscriptionStatusResponse getStatus(String profileId) {
        SubscriptionStatusResponse freePlan = new SubscriptionStatusResponse(false, null, false, null, null);
        if (secretKey == null || secretKey.isBlank()) {
            return freePlan;
        }
        try {
            Profile profile = profileRepository.findById(profileId).orElse(null);
            if (profile == null || profile.getStripeCustomerId() == null || profile.getStripeCustomerId().isBlank()) {
                return freePlan;
            }

            Stripe.apiKey = secretKey;
            SubscriptionCollection subscriptions = com.stripe.model.Subscription.list(
                    SubscriptionListParams.builder()
                            .setCustomer(profile.getStripeCustomerId())
                            .setStatus(SubscriptionListParams.Status.ALL)
                            .setLimit(100L)
                            .build()
            );

            com.stripe.model.Subscription active = subscriptions.getData().stream()
                    .filter(sub -> "active".equals(sub.getStatus()) || "trialing".equals(sub.getStatus()))
                    .findFirst().orElse(null);
            if (active == null) {
                return freePlan;
            }

            List<SubscriptionItem> items = active.getItems().getData();
            String currentProductId = !items.isEmpty() && items.get(0).getPrice() != null
                    ? items.get(0).getPrice().getProduct() : null;
            String currentPeriodEnd = !items.isEmpty() && items.get(0).getCurrentPeriodEnd() != null
                    ? Instant.ofEpochSecond(items.get(0).getCurrentPeriodEnd()).atOffset(ZoneOffset.UTC).toString()
                    : null;

            return new SubscriptionStatusResponse(
                    true, currentProductId, Boolean.TRUE.equals(active.getCancelAtPeriodEnd()),
                    currentPeriodEnd, active.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to fetch subscription status (profileId={})", profileId, e);
            return freePlan;
        }
    }

    public SubscriptionActionResponse cancel(String profileId) {
        return updateCancelAtPeriodEnd(profileId, true, "Subscription will remain active until the end of the current billing period.");
    }

    public SubscriptionActionResponse resume(String profileId) {
        return updateCancelAtPeriodEnd(profileId, false, "Subscription resumed successfully.");
    }

    private SubscriptionActionResponse updateCancelAtPeriodEnd(String profileId, boolean cancelAtPeriodEnd, String successMessage) {
        if (secretKey == null || secretKey.isBlank()) {
            return new SubscriptionActionResponse(false, "Stripe is not configured.");
        }
        try {
            SubscriptionStatusResponse status = getStatus(profileId);
            if (!status.isHasActiveSubscription() || status.getStripeSubscriptionId() == null) {
                return new SubscriptionActionResponse(false, "No active subscription found.");
            }

            Stripe.apiKey = secretKey;
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(status.getStripeSubscriptionId());
            subscription.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(cancelAtPeriodEnd).build());

            return new SubscriptionActionResponse(true, successMessage);
        } catch (Exception e) {
            log.warn("Failed to update subscription cancellation (profileId={}, cancelAtPeriodEnd={})", profileId, cancelAtPeriodEnd, e);
            return new SubscriptionActionResponse(false, e.getMessage());
        }
    }
}

package com.hckcapital.be.service;

import com.hckcapital.be.dto.RedeemGiftResponse;
import com.hckcapital.be.dto.RedeemResponse;
import com.hckcapital.be.dto.VipStatusResponse;
import com.hckcapital.be.model.PointsLog;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.RedeemGift;
import com.hckcapital.be.model.Subscription;
import com.hckcapital.be.repository.PointsLogRepository;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.RedeemGiftRepository;
import com.hckcapital.be.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Ported from the old Next.js reference project's own lib/actions/admin.actions.ts
 * (fetchRedeemGifts/redeemGiftPoints, despite living in that file — both are called by
 * regular users, not admin-only) and workspace.actions.ts (checkUserInSubscription). See
 * RedeemGift's own doc comment for why gifts are real Mongo documents (admin-configurable)
 * unlike missions (a hardcoded config). */
@Service
@RequiredArgsConstructor
public class RedeemService {

    private final RedeemGiftRepository redeemGiftRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PointsLogRepository pointsLogRepository;

    public List<RedeemGiftResponse> listGifts() {
        return redeemGiftRepository.findAll().stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .map(g -> new RedeemGiftResponse(
                        g.getId(), g.getCategory(), g.getName(), g.getMarketValue(),
                        g.getPointsRequired() != null ? g.getPointsRequired() : 0,
                        g.getDaysRequiredText(), g.getSubscriptionDays(), g.getDescription(),
                        g.getTag(), Boolean.TRUE.equals(g.getIsHot()), g.getStock() != null ? g.getStock() : 0))
                .toList();
    }

    /** Mirrors the reference's own redeemGiftPoints exactly, including its VIP-grant
     * mechanism: a "platform"-category gift doesn't set some simple `vipExpiresAt` field —
     * it creates a brand-new local `Subscription` document (totalAmount 0, no
     * stripeSubscriptionId, status "active") and pushes its id onto Profile.subscription,
     * the very same array/model Stripe-backed subscriptions use (see
     * SubscriptionService.createCheckoutSession). getVipStatus below reads that same array
     * back out. */
    public RedeemResponse redeem(ObjectId profileId, String giftId) {
        RedeemGift gift = redeemGiftRepository.findById(giftId).orElse(null);
        if (gift == null) {
            return new RedeemResponse(false, "Gift not found.", null, null, false, null);
        }
        if (!Boolean.TRUE.equals(gift.getIsActive())) {
            return new RedeemResponse(false, "Gift is no longer available.", null, null, false, null);
        }
        int stock = gift.getStock() != null ? gift.getStock() : 0;
        if (stock <= 0) {
            return new RedeemResponse(false, "Gift is out of stock.", null, null, false, null);
        }
        Profile profile = profileRepository.findById(profileId.toHexString()).orElse(null);
        if (profile == null) {
            return new RedeemResponse(false, "Profile not found.", null, null, false, null);
        }

        int pointsRequired = gift.getPointsRequired() != null ? gift.getPointsRequired() : 0;
        int currentPoints = profile.getBubblePoint() != null ? profile.getBubblePoint() : 0;
        if (currentPoints < pointsRequired) {
            return new RedeemResponse(false, "Insufficient points.", null, null, false, null);
        }

        int newPoints = currentPoints - pointsRequired;
        profile.setBubblePoint(newPoints);
        gift.setStock(Math.max(0, stock - 1));

        boolean isVip = "platform".equals(gift.getCategory());
        ObjectId subscriptionDocId = null;
        Date vipEndDate = null;

        if (isVip) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int days = gift.getSubscriptionDays() != null ? gift.getSubscriptionDays() : 3;
            LocalDateTime end = now.plusDays(days);

            Subscription subscription = new Subscription();
            subscription.setPlanStarted(now);
            subscription.setEstimatedEndDate(end);
            subscription.setPaidTerms(1);
            subscription.setTotalAmount(0);
            subscription.setStatus("active");
            subscription = subscriptionRepository.save(subscription);
            subscriptionDocId = new ObjectId(subscription.getId());
            vipEndDate = Date.from(end.toInstant(ZoneOffset.UTC));

            if (profile.getSubscription() == null) {
                profile.setSubscription(new ArrayList<>());
            }
            profile.getSubscription().add(subscriptionDocId);
        }

        PointsLog log = new PointsLog();
        log.setProfileId(profileId);
        log.setPointChanges(-pointsRequired);
        log.setBeforePoints(currentPoints);
        log.setAfterPoints(newPoints);
        log.setSourceType(isVip ? "day_pass_subscription" : "redeem");
        log.setDayPassSubscription(subscriptionDocId);
        log.setRedeemGift(new ObjectId(gift.getId()));
        log.setDescription("Redeemed \"" + gift.getName() + "\" for " + pointsRequired + " pts.");
        Date now = new Date();
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        pointsLogRepository.save(log);

        profileRepository.save(profile);
        redeemGiftRepository.save(gift);

        String endDateIso = vipEndDate != null ? vipEndDate.toInstant().toString() : null;
        return new RedeemResponse(true, "Redeemed successfully!", newPoints, gift.getStock(), isVip, endDateIso);
    }

    /** Mirrors the reference's own checkUserInSubscription — finds the first (not every)
     * active local Subscription doc, same `.find()` semantics (not a sum of multiple active
     * grants). Redeeming a second VIP gift while one is already active does NOT stack days
     * on top of it in the reference app either; this port intentionally keeps that same
     * behavior rather than silently "fixing" it, since that's a product decision, not a bug
     * to fix unasked. */
    public VipStatusResponse getVipStatus(ObjectId profileId) {
        Profile profile = profileRepository.findById(profileId.toHexString()).orElse(null);
        if (profile == null || profile.getSubscription() == null || profile.getSubscription().isEmpty()) {
            return new VipStatusResponse(false, null, 0);
        }

        List<String> subscriptionIds = profile.getSubscription().stream().map(ObjectId::toHexString).toList();
        List<Subscription> subs = subscriptionRepository.findAllById(subscriptionIds);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Subscription active = subs.stream()
                .filter(s -> s.getEstimatedEndDate() != null
                        && !s.getEstimatedEndDate().isBefore(now)
                        && "active".equals(s.getStatus()))
                .findFirst()
                .orElse(null);

        if (active == null) {
            return new VipStatusResponse(false, null, 0);
        }

        long daysRemaining = ChronoUnit.DAYS.between(now, active.getEstimatedEndDate());
        // Reference rounds up (Math.ceil) rather than floors — a plan expiring in 0.2 days
        // shows "1 day left", not "0 days left".
        if (ChronoUnit.HOURS.between(now, active.getEstimatedEndDate()) % 24 != 0) daysRemaining += 1;

        String endDateIso = active.getEstimatedEndDate().toInstant(ZoneOffset.UTC).toString();
        return new VipStatusResponse(true, endDateIso, (int) Math.max(daysRemaining, 0));
    }
}

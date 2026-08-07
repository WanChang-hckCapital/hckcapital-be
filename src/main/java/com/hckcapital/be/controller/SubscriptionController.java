package com.hckcapital.be.controller;

import com.hckcapital.be.dto.CheckoutSessionRequest;
import com.hckcapital.be.dto.CheckoutSessionResponse;
import com.hckcapital.be.dto.ConfirmCheckoutRequest;
import com.hckcapital.be.dto.SubscriptionActionResponse;
import com.hckcapital.be.dto.SubscriptionStatusResponse;
import com.hckcapital.be.service.CardService;
import com.hckcapital.be.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** SubscriptionScreen.tsx's own subscribe/cancel/resume flow — see SubscriptionService's own
 * doc comment for what this ports (and deliberately doesn't) from the old Next.js reference
 * project. Every endpoint resolves the caller's own active profile from the JWT (same
 * pattern as NotificationController) — there's no "manage someone else's subscription" case. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CardService cardService;

    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            Authentication authentication, @RequestBody CheckoutSessionRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.createCheckoutSession(resolveProfileId(authentication), request.getPriceId()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<SubscriptionActionResponse> confirmCheckout(
            Authentication authentication, @RequestBody ConfirmCheckoutRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.confirmCheckout(
                resolveProfileId(authentication), request.getSessionId(), request.getSubscriptionId()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getStatus(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getStatus(resolveProfileId(authentication)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionActionResponse> cancel(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.cancel(resolveProfileId(authentication)));
    }

    @PostMapping("/resume")
    public ResponseEntity<SubscriptionActionResponse> resume(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.resume(resolveProfileId(authentication)));
    }

    private String resolveProfileId(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return profileId.toHexString();
    }
}

package com.hckcapital.be.controller;

import com.hckcapital.be.dto.RedeemGiftResponse;
import com.hckcapital.be.dto.RedeemResponse;
import com.hckcapital.be.dto.VipStatusResponse;
import com.hckcapital.be.service.CardService;
import com.hckcapital.be.service.RedeemService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Sidebar > Mission's own "Points Redemption" tab — see RedeemService for the full port of
 * the old Next.js reference project's own redeemGiftPoints/checkUserInSubscription. */
@RestController
@RequestMapping("/api/v1/redeem")
@RequiredArgsConstructor
public class RedeemController {

    private final RedeemService redeemService;
    private final CardService cardService;

    @GetMapping("/gifts")
    public ResponseEntity<List<RedeemGiftResponse>> listGifts() {
        return ResponseEntity.ok(redeemService.listGifts());
    }

    @GetMapping("/vip-status")
    public ResponseEntity<VipStatusResponse> getVipStatus(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return ResponseEntity.ok(redeemService.getVipStatus(profileId));
    }

    @PostMapping
    public ResponseEntity<?> redeem(Authentication authentication, @RequestBody Map<String, String> body) {
        String giftId = body.get("giftId");
        if (giftId == null || giftId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "giftId is required"));
        }
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return ResponseEntity.ok(redeemService.redeem(profileId, giftId));
    }
}

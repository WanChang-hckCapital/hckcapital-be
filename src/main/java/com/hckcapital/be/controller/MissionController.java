package com.hckcapital.be.controller;

import com.hckcapital.be.dto.MissionActionResponse;
import com.hckcapital.be.dto.MissionsResponse;
import com.hckcapital.be.service.CardService;
import com.hckcapital.be.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Sidebar > Mission's own "Missions" tab — see MissionService for the full port of the old
 * Next.js reference project's own mission.actions.ts. `cardService.resolveActiveProfileId`
 * is the same helper every other per-profile endpoint in this backend already uses to go
 * from the JWT-resolved memberId to the actual active Profile. */
@RestController
@RequestMapping("/api/v1/profile/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;
    private final CardService cardService;

    @GetMapping
    public ResponseEntity<MissionsResponse> getMissions(
            Authentication authentication,
            @RequestParam(defaultValue = MissionService.PERIOD_DAILY) String period
    ) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return ResponseEntity.ok(missionService.getMissions(profileId, period));
    }

    @PostMapping("/check-in")
    public ResponseEntity<MissionActionResponse> checkIn(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return ResponseEntity.ok(missionService.checkIn(profileId));
    }

    @PostMapping("/claim")
    public ResponseEntity<?> claimReward(Authentication authentication, @RequestBody Map<String, String> body) {
        String missionType = body.get("missionType");
        String period = body.getOrDefault("period", MissionService.PERIOD_DAILY);
        if (missionType == null || missionType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missionType is required"));
        }
        String memberId = (String) authentication.getPrincipal();
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        return ResponseEntity.ok(missionService.claimReward(profileId, missionType, period));
    }
}

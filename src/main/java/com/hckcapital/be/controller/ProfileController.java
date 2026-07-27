package com.hckcapital.be.controller;

import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CollectionSummaryResponse;
import com.hckcapital.be.dto.CreateCollectionRequest;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.ProfileResponse;
import com.hckcapital.be.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getProfile(profileId));
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getFollowers(profileId));
    }

    @GetMapping("/following")
    public ResponseEntity<List<FollowUserResponse>> getFollowing(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getFollowing(profileId));
    }

    @GetMapping("/collections")
    public ResponseEntity<List<CollectionSummaryResponse>> getCollections(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getCollections(profileId));
    }

    @PostMapping("/collections")
    public ResponseEntity<?> createCollection(Authentication authentication, @Valid @RequestBody CreateCollectionRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.createCollection(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/collections/{collectionId}")
    public ResponseEntity<?> updateCollection(
            Authentication authentication,
            @PathVariable String collectionId,
            @Valid @RequestBody CreateCollectionRequest request
    ) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.updateCollection(memberId, collectionId, request));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/collections/{collectionId}/cards")
    public ResponseEntity<List<CardSummaryResponse>> getCollectionCards(
            @PathVariable String collectionId,
            @RequestParam(required = false) String viewerProfileId
    ) {
        return ResponseEntity.ok(profileService.getCollectionCards(collectionId, viewerProfileId));
    }

    @GetMapping("/cards/published")
    public ResponseEntity<CardPageResponse> getPublishedCards(
            @RequestParam String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String viewerProfileId
    ) {
        return ResponseEntity.ok(profileService.getPublishedCards(profileId, page, limit, viewerProfileId));
    }

    @GetMapping("/cards/drafts")
    public ResponseEntity<CardPageResponse> getDraftCards(
            @RequestParam String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String viewerProfileId
    ) {
        return ResponseEntity.ok(profileService.getDraftCards(profileId, page, limit, viewerProfileId));
    }

    @GetMapping("/cards/recyclebin")
    public ResponseEntity<CardPageResponse> getRecycleBinCards(
            @RequestParam String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String viewerProfileId
    ) {
        return ResponseEntity.ok(profileService.getRecycleBinCards(profileId, page, limit, viewerProfileId));
    }
}

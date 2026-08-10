package com.hckcapital.be.controller;

import com.hckcapital.be.dto.AccountDetailsResponse;
import com.hckcapital.be.dto.AffiliateStatusResponse;
import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CollectionSummaryResponse;
import com.hckcapital.be.dto.CreateCollectionRequest;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.OnboardRequest;
import com.hckcapital.be.dto.PointsHistoryResponse;
import com.hckcapital.be.dto.ProfileResponse;
import com.hckcapital.be.dto.ReferralResponse;
import com.hckcapital.be.dto.DailySeriesPointResponse;
import com.hckcapital.be.dto.ReportOverviewResponse;
import com.hckcapital.be.dto.UpdateAccountTypeRequest;
import com.hckcapital.be.dto.UpdateNotificationSettingsRequest;
import com.hckcapital.be.dto.UpdatePreferencesRequest;
import com.hckcapital.be.dto.UpdateProfileDetailsRequest;
import com.hckcapital.be.dto.UpdatePushTokenRequest;
import com.hckcapital.be.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    // See ProfileService.recordProfileView — called by ProfileScreen.tsx when opening
    // someone else's profile, so Report > Overview's "Profile Views" card reflects real
    // data. viewerProfileId is the caller's own active profile (already known client-side);
    // no-ops server-side on a self-view or missing viewer id.
    @PostMapping("/view")
    public ResponseEntity<Void> recordProfileView(
            @RequestParam String profileId,
            @RequestParam(required = false) String viewerProfileId
    ) {
        profileService.recordProfileView(profileId, viewerProfileId);
        return ResponseEntity.noContent().build();
    }

    // See AppNavigator.tsx's onboarding gate + OnboardingScreen.tsx on the RN side.
    @PostMapping("/onboard")
    public ResponseEntity<?> completeOnboarding(Authentication authentication, @Valid @RequestBody OnboardRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.completeOnboarding(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getNotificationSettings/updateNotificationSettings — Settings > Notification.
    @GetMapping("/notification-settings")
    public ResponseEntity<?> getNotificationSettings(Authentication authentication) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.getNotificationSettings(memberId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/notification-settings")
    public ResponseEntity<?> updateNotificationSettings(Authentication authentication, @RequestBody UpdateNotificationSettingsRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.updateNotificationSettings(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.updatePushToken — called on login/app start once expo-notifications
    // hands the RN app a token, so ExpoPushService knows where to actually deliver a push.
    @PutMapping("/push-token")
    public ResponseEntity<?> updatePushToken(Authentication authentication, @RequestBody UpdatePushTokenRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            profileService.updatePushToken(memberId, request.getExpoPushToken());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getReportOverview — Settings > Report's own "Overview" tab and
    // period dropdown. startDate/endDate are "yyyy-MM-dd" (see parseStartOfDay/parseEndOfDay
    // below), either omittable to leave that side of the range open.
    @GetMapping("/report/overview")
    public ResponseEntity<ReportOverviewResponse> getReportOverview(
            Authentication authentication,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getReportOverview(memberId, parseStartOfDay(startDate), parseEndOfDay(endDate)));
    }

    // See ProfileService.getProfileViewsSeries — the line graph below the Report screen's
    // stat cards. Unlike /report/overview, both bounds are required (a graph has no
    // meaningful unbounded axis).
    @GetMapping("/report/profile-views-series")
    public ResponseEntity<?> getProfileViewsSeries(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        try {
            String memberId = (String) authentication.getPrincipal();
            List<DailySeriesPointResponse> series =
                    profileService.getProfileViewsSeries(memberId, parseStartOfDay(startDate), parseEndOfDay(endDate));
            return ResponseEntity.ok(series);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getFollowersSeries — the Report screen's followers line graph
    // (cumulative total per day, not new-followers-per-day like profile-views-series).
    @GetMapping("/report/followers-series")
    public ResponseEntity<?> getFollowersSeries(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        try {
            String memberId = (String) authentication.getPrincipal();
            List<DailySeriesPointResponse> series =
                    profileService.getFollowersSeries(memberId, parseStartOfDay(startDate), parseEndOfDay(endDate));
            return ResponseEntity.ok(series);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getAccountDetails — Settings > Manage's own read-only email/country/phone.
    @GetMapping("/account-details")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getAccountDetails(memberId));
    }

    // See ProfileService.getAffiliateStatus/RewardfulService — Sidebar.tsx's own "Join
    // Affiliates" (FLEXADMIN-only) vs "My Affiliate" (existing-affiliate-only) gating.
    @GetMapping("/affiliate-status")
    public ResponseEntity<AffiliateStatusResponse> getAffiliateStatus(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getAffiliateStatus(memberId));
    }

    // See ProfileService.getReferralInfo — Sidebar.tsx's own "Referral" item.
    @GetMapping("/referral")
    public ResponseEntity<ReferralResponse> getReferralInfo(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getReferralInfo(memberId));
    }

    // See ProfileService.getPointsHistory — MissionScreen.tsx's own "Points Log" tab.
    @GetMapping("/points")
    public ResponseEntity<PointsHistoryResponse> getPointsHistory(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getPointsHistory(memberId));
    }

    // See ProfileService.updateProfileDetails — Settings > Manage's own edit form.
    @PutMapping("/details")
    public ResponseEntity<?> updateProfileDetails(Authentication authentication, @Valid @RequestBody UpdateProfileDetailsRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.updateProfileDetails(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.updateAccountType — the Settings > Privacy toggle's own write endpoint.
    @PutMapping("/account-type")
    public ResponseEntity<?> updateAccountType(Authentication authentication, @Valid @RequestBody UpdateAccountTypeRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.updateAccountType(memberId, request.getAccountType()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getPreferences/updatePreferences — Settings > Theme/Language/Preference.
    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences(Authentication authentication) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.getPreferences(memberId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(Authentication authentication, @RequestBody UpdatePreferencesRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.updatePreferences(memberId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getFollowers(profileId));
    }

    @GetMapping("/following")
    public ResponseEntity<List<FollowUserResponse>> getFollowing(@RequestParam String profileId) {
        return ResponseEntity.ok(profileService.getFollowing(profileId));
    }

    // See ProfileService.getBlockedAccounts/unblockAccount — Settings > Block Account.
    @GetMapping("/blocked-accounts")
    public ResponseEntity<?> getBlockedAccounts(Authentication authentication) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.getBlockedAccounts(memberId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/blocked-accounts/{blockedProfileId}")
    public ResponseEntity<?> unblockAccount(Authentication authentication, @PathVariable String blockedProfileId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            profileService.unblockAccount(memberId, blockedProfileId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // See ProfileService.getMutedAccounts/unmuteAccount — Settings > Muted Accounts.
    @GetMapping("/muted-accounts")
    public ResponseEntity<?> getMutedAccounts(Authentication authentication) {
        try {
            String memberId = (String) authentication.getPrincipal();
            return ResponseEntity.ok(profileService.getMutedAccounts(memberId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/muted-accounts/{mutedProfileId}")
    public ResponseEntity<?> unmuteAccount(Authentication authentication, @PathVariable String mutedProfileId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            profileService.unmuteAccount(memberId, mutedProfileId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
            @RequestParam(required = false) String viewerProfileId,
            // See Settings > Report's own period dropdown (ReportSection.tsx) — "yyyy-MM-dd",
            // either omittable. Every other caller of this endpoint (ProfileScreen.tsx's own
            // published-cards tab) just omits both, same unfiltered behavior as before.
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        if (startDate == null && endDate == null) {
            return ResponseEntity.ok(profileService.getPublishedCards(profileId, page, limit, viewerProfileId));
        }
        return ResponseEntity.ok(profileService.getPublishedCards(
                profileId, page, limit, viewerProfileId, parseStartOfDay(startDate), parseEndOfDay(endDate)));
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

    /** "yyyy-MM-dd" → start of that day, or null if the input itself is null/blank — shared
     * by every date-range param above (Settings > Report's own period dropdown). */
    private static LocalDateTime parseStartOfDay(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atStartOfDay();
    }

    /** "yyyy-MM-dd" → the last instant of that day (23:59:59.999999999), so an inclusive
     * `.lte(...)` range actually includes everything created on the end date itself. */
    private static LocalDateTime parseEndOfDay(String date) {
        if (date == null || date.isBlank()) return null;
        return LocalDate.parse(date).atTime(LocalTime.MAX);
    }
}

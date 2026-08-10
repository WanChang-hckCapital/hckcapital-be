package com.hckcapital.be.service;

import com.hckcapital.be.dto.AccountDetailsResponse;
import com.hckcapital.be.dto.AffiliateStatusResponse;
import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CollectionSummaryResponse;
import com.hckcapital.be.dto.CreateCollectionRequest;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.NotificationSettingsResponse;
import com.hckcapital.be.dto.OnboardRequest;
import com.hckcapital.be.dto.OnboardResponse;
import com.hckcapital.be.dto.PointsHistoryResponse;
import com.hckcapital.be.dto.PointsLogEntryResponse;
import com.hckcapital.be.dto.PreferencesResponse;
import com.hckcapital.be.dto.ProfileResponse;
import com.hckcapital.be.dto.ReferralResponse;
import com.hckcapital.be.dto.DailySeriesPointResponse;
import com.hckcapital.be.dto.ReportOverviewResponse;
import com.hckcapital.be.dto.UpdateNotificationSettingsRequest;
import com.hckcapital.be.dto.UpdatePreferencesRequest;
import com.hckcapital.be.dto.UpdateProfileDetailsRequest;
import com.hckcapital.be.model.Card;
import com.hckcapital.be.model.CardView;
import com.hckcapital.be.model.Collection;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.MissionProgress;
import com.hckcapital.be.model.PointsLog;
import com.hckcapital.be.model.ProfileView;
import com.hckcapital.be.model.RedeemGift;
import com.hckcapital.be.model.ReferralHistory;
import com.hckcapital.be.repository.CollectionRepository;
import com.hckcapital.be.repository.MemberRepository;
import com.hckcapital.be.repository.MissionProgressRepository;
import com.hckcapital.be.repository.PointsLogRepository;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.RedeemGiftRepository;
import com.hckcapital.be.repository.ReferralHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final MemberRepository memberRepository;
    private final CollectionRepository collectionRepository;
    private final CardService cardService;
    private final MongoTemplate mongoTemplate;
    private final RewardfulService rewardfulService;
    private final ReferralHistoryRepository referralHistoryRepository;
    private final PointsLogRepository pointsLogRepository;
    private final RedeemGiftRepository redeemGiftRepository;
    private final MissionProgressRepository missionProgressRepository;

    // Same public chatroom every fresh onboarding gets auto-joined to as the old Next.js
    // reference project's own OnboardingComponent.tsx (see inviteToPublicChatroom below).
    @Value("${flxbubble.public-group:}")
    private String publicChatroomId;

    public ProfileResponse getProfile(String profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        int followersCount = profile.getFollowers() != null ? profile.getFollowers().size() : 0;
        int followingCount = profile.getFollowing() != null ? profile.getFollowing().size() : 0;
        int cardCount = countPublishedCards(profileId);
        int draftCount = countDraftCards(profileId);

        return new ProfileResponse(
                profile.getId(),
                profile.getAccountname(),
                profile.getImageFilePath(),
                profile.getShortdescription(),
                profile.getUsertype(),
                profile.getAccountType(),
                profile.getRole(),
                followersCount,
                followingCount,
                cardCount,
                draftCount
        );
    }

    /** Settings > Notification's own read — see updateNotificationSettings below for the
     * write side. Mirrors the old Next.js reference project's own checkNotificationSetting. */
    public NotificationSettingsResponse getNotificationSettings(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Profile.Notifications n = profile.getNotifications() != null ? profile.getNotifications() : new Profile.Notifications();
        return new NotificationSettingsResponse(n.getEmail(), n.getInApp());
    }

    /** Mirrors the old Next.js reference project's own updateEmailNotification/
     * updateInAppNotification — two separate actions there, collapsed into one endpoint
     * here, same as updatePreferences above. Only touches the field the caller actually
     * sent. */
    public NotificationSettingsResponse updateNotificationSettings(String memberId, UpdateNotificationSettingsRequest request) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Profile.Notifications n = profile.getNotifications();
        if (n == null) {
            n = new Profile.Notifications();
            profile.setNotifications(n);
        }
        if (request.getEmailNotification() != null) n.setEmail(request.getEmailNotification());
        if (request.getInAppNotification() != null) n.setInApp(request.getInAppNotification());
        profileRepository.save(profile);
        return new NotificationSettingsResponse(n.getEmail(), n.getInApp());
    }

    /** Registers/clears this profile's Expo push token — see ExpoPushService/
     * NotificationService for how it's actually used (only pushed to when the recipient
     * isn't currently online per RedisPresenceService). Called on login/app start and
     * whenever expo-notifications hands the RN app a fresh token. */
    public void updatePushToken(String memberId, String expoPushToken) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        profile.setExpoPushToken(expoPushToken);
        profileRepository.save(profile);
    }

    /** Settings > Manage's own read-only email/country/phone display — see
     * UpdateProfileDetailsRequest's own doc comment on why those three stay out of the
     * editable form (email is set at signup, country/phone are a one-time onboarding step
     * in this app, not a repeated-edit field). Sourced from Member, not Profile — see
     * AccountDetailsResponse's own doc comment on why this needs its own JWT-only endpoint
     * rather than reusing the public profileId-keyed GET /api/v1/profile. */
    public AccountDetailsResponse getAccountDetails(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return new AccountDetailsResponse(member.getEmail(), member.getCountry(), member.getCountrycode(), member.getPhone());
    }

    /** Sidebar.tsx's own "Join Affiliates"/"My Affiliate" gating — see
     * RewardfulService.getAffiliateStatus's own doc comment. Same Member.email (not
     * Profile.email) getAccountDetails above uses, for the same reason: it's the real
     * login/auth email, which is what a user would have actually typed into Rewardful's own
     * signup form. */
    public AffiliateStatusResponse getAffiliateStatus(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return rewardfulService.getAffiliateStatus(member.getEmail());
    }

    /** Sidebar > Referral — see ReferralResponse's own doc comment. Scoped to the caller's
     * active profile, same resolveActiveProfileId this backend's other per-profile reads
     * already use. `referralCode` can come back null/blank for a profile created via Google
     * Sign-In (see AuthService.loginWithGoogle's own doc comment — that path never generates
     * one, unlike email/password signup); the screen handles that case itself rather than
     * this backend inventing a code on the fly for a read-only endpoint. */
    public ReferralResponse getReferralInfo(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Query query = new Query(Criteria.where("referrerId").is(profileId).and("status").is("completed"));
        long successfulReferralsCount = mongoTemplate.count(query, ReferralHistory.class);
        return new ReferralResponse(profile.getReferralCode(), (int) successfulReferralsCount);
    }

    /** Sidebar > Mission > Points Log tab — mirrors the old Next.js reference project's own
     * loadPersonalPoints (lib/actions/user.actions.ts) field-for-field, including its exact
     * "top 50 newest, no cursor pagination" cap. `currentPoints` is the most recent log's
     * own afterPoints (not Profile.bubblePoint read directly) — same as that reference
     * function, so a log write and the displayed balance can never disagree even for a
     * moment mid-request.
     *
     * The reference resolves display fields (mission type/period, gift name, referrer/
     * referee names) via Mongoose `.populate()` chains; this does the same job with plain
     * batched repository finds instead — one findAllById per related collection rather than
     * a join, which is the idiomatic Spring Data equivalent here. */
    public PointsHistoryResponse getPointsHistory(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        List<PointsLog> logs = pointsLogRepository.findByProfileIdOrderByCreatedAtDesc(
                profileId, org.springframework.data.domain.PageRequest.of(0, 50));

        List<String> missionIds = logs.stream().map(PointsLog::getMission).filter(Objects::nonNull)
                .map(ObjectId::toHexString).distinct().toList();
        List<String> referralIds = logs.stream().map(PointsLog::getReferral).filter(Objects::nonNull)
                .map(ObjectId::toHexString).distinct().toList();
        List<String> giftIds = logs.stream().map(PointsLog::getRedeemGift).filter(Objects::nonNull)
                .map(ObjectId::toHexString).distinct().toList();

        Map<String, MissionProgress> missionsById = missionProgressRepository.findAllById(missionIds).stream()
                .collect(Collectors.toMap(MissionProgress::getId, m -> m));
        Map<String, ReferralHistory> referralsById = referralHistoryRepository.findAllById(referralIds).stream()
                .collect(Collectors.toMap(ReferralHistory::getId, r -> r));
        Map<String, RedeemGift> giftsById = redeemGiftRepository.findAllById(giftIds).stream()
                .collect(Collectors.toMap(RedeemGift::getId, g -> g));

        List<String> referralProfileIds = referralsById.values().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReferrerId(), r.getRefereeId()))
                .filter(Objects::nonNull).map(ObjectId::toHexString).distinct().toList();
        Map<String, String> accountNameByProfileId = profileRepository.findAllById(referralProfileIds).stream()
                .collect(Collectors.toMap(Profile::getId, Profile::getAccountname));

        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT;
        List<PointsLogEntryResponse> entries = logs.stream().map(log -> {
            MissionProgress mission = log.getMission() != null ? missionsById.get(log.getMission().toHexString()) : null;
            ReferralHistory referral = log.getReferral() != null ? referralsById.get(log.getReferral().toHexString()) : null;
            RedeemGift gift = log.getRedeemGift() != null ? giftsById.get(log.getRedeemGift().toHexString()) : null;

            return new PointsLogEntryResponse(
                    log.getId(),
                    log.getPointChanges() != null ? log.getPointChanges() : 0,
                    log.getBeforePoints() != null ? log.getBeforePoints() : 0,
                    log.getAfterPoints() != null ? log.getAfterPoints() : 0,
                    log.getSourceType(),
                    log.getDescription(),
                    log.getCreatedAt() != null ? isoFormatter.format(log.getCreatedAt().toInstant()) : null,
                    mission != null ? mission.getMissionType() : null,
                    mission != null ? mission.getPeriod() : null,
                    gift != null ? gift.getName() : null,
                    referral != null ? referral.getReferralCode() : null,
                    referral != null && referral.getReferrerId() != null
                            ? accountNameByProfileId.get(referral.getReferrerId().toHexString()) : null,
                    referral != null && referral.getRefereeId() != null
                            ? accountNameByProfileId.get(referral.getRefereeId().toHexString()) : null
            );
        }).toList();

        int currentPoints = !logs.isEmpty() && logs.get(0).getAfterPoints() != null ? logs.get(0).getAfterPoints() : 0;
        return new PointsHistoryResponse(currentPoints, entries);
    }

    /** Settings > Manage's own edit form — mirrors the old Next.js reference project's own
     * updateProfileDetails (lib/actions/user.actions.ts), minus its email param (Member.email
     * is set at signup and never re-collected here, same as OnboardRequest) and minus that
     * reference's own multi-profile switcher (ProfileList.tsx/PersonalProfileEditForm.tsx) —
     * this backend has no Organization/Entrepreneur profile subsystem for a member to switch
     * between, so there's only ever the one active profile to edit. Unlike the reference
     * (which unconditionally overwrites accountname/email even with an empty string, and has
     * a copy-paste bug returning "Profile not found" as its own success message), this only
     * touches shortdescription/imageFilePath when the caller actually sent them. */
    public ProfileResponse updateProfileDetails(String memberId, UpdateProfileDetailsRequest request) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        profile.setAccountname(request.getAccountname());
        if (request.getShortdescription() != null) {
            profile.setShortdescription(request.getShortdescription());
        }
        if (request.getImageFilePath() != null && !request.getImageFilePath().isBlank()) {
            profile.setImageFilePath(request.getImageFilePath());
        }
        profileRepository.save(profile);
        return getProfile(profile.getId());
    }

    /** Toggles between "PUBLIC"/"PRIVATE" — mirrors the old Next.js reference project's own
     * updateAccountType (lib/actions/user.actions.ts), just resolving the caller's own
     * active profile from the JWT instead of taking a profileId param, same as every other
     * self-service mutation here (see completeOnboarding/createCollection). The value is
     * already enforced elsewhere on this backend (CardService's own PUBLIC-or-owner
     * card-feed filters) and on the RN side (FriendScreen.tsx's follow-vs-follow-request
     * branch) — this endpoint is just the missing write side; see
     * SettingsScreen.tsx's Privacy toggle on the RN side. */
    public ProfileResponse updateAccountType(String memberId, String accountType) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        profile.setAccountType(accountType);
        profileRepository.save(profile);
        return getProfile(profile.getId());
    }

    /** Settings > Theme/Language/Preference screens' own read — see updatePreferences below
     * for the write side. Defaults to Profile.Preferences' own field initializers (a fresh
     * "light"/"en"/[]/false) if the profile somehow has no preferences sub-document yet
     * (only possible for a profile that predates this backend's own defaulting). */
    public PreferencesResponse getPreferences(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Profile.Preferences prefs = profile.getPreferences() != null ? profile.getPreferences() : new Profile.Preferences();
        return new PreferencesResponse(prefs.getTheme(), prefs.getLanguage(), prefs.getCategories(), prefs.getIsSkip());
    }

    /** Mirrors the old Next.js reference project's own updateProfileThemePreference/
     * updateUserLanguagePreference/saveProfilePreferences — three separate actions there,
     * collapsed into one endpoint here since they all just set different fields on the same
     * Preferences sub-document. Only touches the fields the caller actually sent (see
     * UpdatePreferencesRequest's own doc comment); resolves the caller's own active profile
     * from the JWT, same as updateAccountType above. */
    public PreferencesResponse updatePreferences(String memberId, UpdatePreferencesRequest request) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Profile.Preferences prefs = profile.getPreferences();
        if (prefs == null) {
            prefs = new Profile.Preferences();
            profile.setPreferences(prefs);
        }
        if (request.getTheme() != null) prefs.setTheme(request.getTheme());
        if (request.getLanguage() != null) prefs.setLanguage(request.getLanguage());
        if (request.getCategories() != null) prefs.setCategories(request.getCategories());
        if (request.getIsSkip() != null) prefs.setIsSkip(request.getIsSkip());
        profileRepository.save(profile);
        return new PreferencesResponse(prefs.getTheme(), prefs.getLanguage(), prefs.getCategories(), prefs.getIsSkip());
    }

    /** Mirrors the old Next.js reference project's own updateMemberDetails (called from
     * OnboardingComponent.tsx): sets Profile's accountname/shortdescription/imageFilePath/
     * onboarded, and Member's phone/country/countrycode — same split across the two
     * documents as the reference. Doesn't touch Member.email (already set at signup, not
     * collected again here — see OnboardRequest's own doc comment on why the reference's
     * own read-only email field was dropped). memberId comes from the JWT (see
     * ProfileController.completeOnboarding's own Authentication param), not a request body
     * field — you can only ever onboard your own account this way. Deliberately still skips
     * the reference's own referral/points bookkeeping in that same action — never ported to
     * this backend at all, see Profile.referralCode's own doc comment. */
    public OnboardResponse completeOnboarding(String memberId, OnboardRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        List<ObjectId> profileIds = member.getProfiles();
        if (profileIds == null || profileIds.isEmpty() || member.getActiveProfile() >= profileIds.size()) {
            throw new RuntimeException("No active profile found for this account");
        }
        String profileId = profileIds.get(member.getActiveProfile()).toHexString();

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        // Captured before overwriting — awardOnboardingCompletionPoints below must only ever
        // fire the first time a profile actually completes onboarding, not on every call
        // (the RN app's own navigation gating already keeps it from being reachable again
        // once onboarded, but guarding here too is what actually stops a repeat call from
        // double-awarding bubblePoint rather than relying on client behavior alone).
        boolean wasAlreadyOnboarded = Boolean.TRUE.equals(profile.getOnboarded());

        profile.setAccountname(request.getAccountname());
        profile.setShortdescription(request.getShortdescription());
        if (request.getImageFilePath() != null && !request.getImageFilePath().isBlank()) {
            profile.setImageFilePath(request.getImageFilePath());
        }
        profile.setOnboarded(true);
        profileRepository.save(profile);

        member.setPhone(request.getPhone());
        member.setCountry(request.getCountry());
        member.setCountrycode(request.getCountrycode());
        memberRepository.save(member);

        inviteToPublicChatroom(profileId);
        ensureDefaultCollections(new ObjectId(profileId));

        if (!wasAlreadyOnboarded) {
            awardOnboardingCompletionPoints(profile);
        }

        return new OnboardResponse(profile.getAccountname(), profile.getShortdescription(), profile.getImageFilePath(), true);
    }

    // Mirrors the old Next.js reference project's own bubblePointConstants.ts.
    private static final int FRIENDS_FINISH_SETUP_POINTS = 200;
    private static final int NEW_USER_POINTS = 100;
    private static final int SIGN_UP_W_LINK_BONUS = 50;

    /** Ported from the old Next.js reference project's own updateMemberDetails (lib/
     * actions/user.actions.ts) — the reward-payout half of the referral flow, triggered by
     * completeOnboarding above (AuthService.signup's own applyReferralCode only writes the
     * "uncompleted" ReferralHistory row; nothing gets paid out until onboarding finishes).
     *
     * Two cases, matching the reference exactly:
     * - An "uncompleted" ReferralHistory row exists for this profile as referee (they signed
     *   up with a valid `?ref=`/typed code): flip it to "completed", pay the REFERRER
     *   FRIENDS_FINISH_SETUP_POINTS (200), and pay this REFEREE both NEW_USER_POINTS (100)
     *   and SIGN_UP_W_LINK_BONUS (50) — 150 total.
     * - No such row (organic signup, or a referral that's already been consumed/invalid):
     *   pay just NEW_USER_POINTS (100) to this profile as a plain completion bonus.
     * Every bubblePoint change is paired with a PointsLog row, same audit-trail convention
     * the reference itself follows (loadPersonalPoints on that side reads this same
     * collection back out for the Points dashboard). */
    private void awardOnboardingCompletionPoints(Profile referee) {
        ObjectId refereeId = new ObjectId(referee.getId());
        ReferralHistory referral = referralHistoryRepository
                .findByRefereeIdAndStatus(refereeId, "uncompleted")
                .orElse(null);

        if (referral == null) {
            int before = referee.getBubblePoint() != null ? referee.getBubblePoint() : 0;
            int after = before + NEW_USER_POINTS;
            referee.setBubblePoint(after);
            profileRepository.save(referee);
            logPoints(refereeId, NEW_USER_POINTS, before, after, null,
                    "Welcome bonus for completing your account profile!");
            return;
        }

        referral.setStatus("completed");
        referral.setRewardPoints(FRIENDS_FINISH_SETUP_POINTS);
        referral.setUpdatedAt(new Date());
        referralHistoryRepository.save(referral);
        ObjectId referralId = new ObjectId(referral.getId());

        profileRepository.findById(referral.getReferrerId().toHexString()).ifPresent(referrer -> {
            int before = referrer.getBubblePoint() != null ? referrer.getBubblePoint() : 0;
            int after = before + FRIENDS_FINISH_SETUP_POINTS;
            referrer.setBubblePoint(after);
            profileRepository.save(referrer);
            logPoints(new ObjectId(referrer.getId()), FRIENDS_FINISH_SETUP_POINTS, before, after, referralId,
                    "Successfully referred a friend (" + referee.getAccountname() + ")!");
        });

        int totalRefereeBonus = NEW_USER_POINTS + SIGN_UP_W_LINK_BONUS;
        int refereeBefore = referee.getBubblePoint() != null ? referee.getBubblePoint() : 0;
        int refereeAfter = refereeBefore + totalRefereeBonus;
        referee.setBubblePoint(refereeAfter);
        profileRepository.save(referee);
        logPoints(refereeId, totalRefereeBonus, refereeBefore, refereeAfter, referralId,
                "Welcome bonus for signing up using a referral link!");
    }

    private void logPoints(ObjectId profileId, int pointChanges, int beforePoints, int afterPoints,
                            ObjectId referral, String description) {
        PointsLog log = new PointsLog();
        log.setProfileId(profileId);
        log.setPointChanges(pointChanges);
        log.setBeforePoints(beforePoints);
        log.setAfterPoints(afterPoints);
        log.setSourceType("referral");
        log.setReferral(referral);
        log.setDescription(description);
        Date now = new Date();
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        pointsLogRepository.save(log);
    }

    /** Mirrors the old Next.js reference project's own migrateExistingProfilesDefaultCollections
     * (see hckcapital/lib/actions/user.actions.ts) — same two default collections
     * ("NAMECARD" public, "CONVERSATION" private), same idempotent "only create the ones
     * missing" check via findByCreatorAndName (matching that function's own
     * CollectionModel.find({creator, name: {$in: [...]}}) pre-check), just run once per
     * profile right when it's actually created/onboarded instead of as a standalone bulk
     * migration over every existing profile — there's no equivalent bulk-backfill need
     * here since this backend has no legacy profiles that predate this field. */
    private static final String[] DEFAULT_COLLECTION_NAMES = {"NAMECARD", "CONVERSATION"};

    private void ensureDefaultCollections(ObjectId profileId) {
        for (String name : DEFAULT_COLLECTION_NAMES) {
            if (collectionRepository.findByCreatorAndName(profileId, name).isPresent()) continue;

            Collection collection = new Collection();
            collection.setName(name);
            collection.setCreator(profileId);
            collection.setPublicStatus("NAMECARD".equals(name) ? Collection.PublicStatus.PUBLIC : Collection.PublicStatus.PRIVATE);
            collection.setIsCustom(false);
            collection.setCards(List.of());
            LocalDateTime now = LocalDateTime.now();
            collection.setCreatedAt(now);
            collection.setUpdatedAt(now);
            collectionRepository.save(collection);
        }
    }

    /** Mirrors the old Next.js reference project's own inviteToPublicChatroom (see
     * hckcapital/lib/actions/user.actions.ts) — adds this profile to the shared public
     * chatroom's participants list. No Chatroom model/collection exists elsewhere in this
     * backend yet (chat isn't ported beyond this), so this reaches the "chatrooms"
     * collection directly via MongoTemplate with a targeted $addToSet update rather than
     * mapping the whole document — reading the full document into a partially-modeled Java
     * class and saving it back would silently wipe out every field this backend doesn't
     * know about (admin lists, mutes, group image, etc.), which a plain repository
     * save() would do.
     *
     * Best-effort and non-blocking, same as the reference: if NEXT_PUBLIC_
     * FLXBUBBLE_PUBLIC_GROUP isn't configured, or the chatroom doesn't exist, or the update
     * otherwise fails, this logs and moves on rather than failing the onboarding
     * submission over what the reference itself treats as a side effect (its own caller
     * never even checks this call's return value). */
    private void inviteToPublicChatroom(String profileId) {
        if (publicChatroomId == null || publicChatroomId.isBlank()) return;
        try {
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(publicChatroomId)));
            Update update = new Update().addToSet("participants", new ObjectId(profileId));
            var result = mongoTemplate.updateFirst(query, update, "chatrooms");
            if (result.getMatchedCount() == 0) {
                log.warn("Public chatroom {} not found — skipping onboarding invite for profile {}", publicChatroomId, profileId);
            }
        } catch (Exception e) {
            log.warn("Failed to add profile {} to public chatroom {}: {}", profileId, publicChatroomId, e.getMessage());
        }
    }

    public List<FollowUserResponse> getFollowers(String profileId) {
        Profile profile = profileRepository.findById(profileId).orElse(null);
        if (profile == null || profile.getFollowers() == null || profile.getFollowers().isEmpty()) {
            return List.of();
        }

        List<ObjectId> followerIds = profile.getFollowers().stream()
                .map(Profile.Follower::getFollowersId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (followerIds.isEmpty()) return List.of();

        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(followerIds)),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    public List<FollowUserResponse> getFollowing(String profileId) {
        Profile profile = profileRepository.findById(profileId).orElse(null);
        if (profile == null || profile.getFollowing() == null || profile.getFollowing().isEmpty()) {
            return List.of();
        }

        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(profile.getFollowing())),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    /** Settings > Block Account's own list — the caller's own active profile's
     * blockedAccounts, hydrated the same way getFollowers/getFollowing hydrate their own id
     * lists. Mirrors the old Next.js reference project's own getBlockedAccounts. */
    public List<FollowUserResponse> getBlockedAccounts(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        if (profile.getBlockedAccounts() == null || profile.getBlockedAccounts().isEmpty()) {
            return List.of();
        }
        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(profile.getBlockedAccounts())),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    /** Mirrors the old Next.js reference project's own unblockAccount — removes one entry
     * from the caller's own blockedAccounts. This is the review-and-undo half only;
     * blocking someone in the first place isn't wired up anywhere in this app yet (the
     * reference itself only triggers it from a friend-list row and a chat "info" sheet,
     * neither of which exist in this app currently — see Settings > Block Account's own
     * doc comment on the RN side). */
    public void unblockAccount(String memberId, String blockedProfileId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        if (profile.getBlockedAccounts() != null) {
            profile.getBlockedAccounts().removeIf(id -> id.toHexString().equals(blockedProfileId));
            profileRepository.save(profile);
        }
    }

    /** Settings > Muted Accounts' own list — same shape as getBlockedAccounts above, just
     * against mutedAccounts. Mirrors the old Next.js reference project's own
     * getMutedAccounts. */
    public List<FollowUserResponse> getMutedAccounts(String memberId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        if (profile.getMutedAccounts() == null || profile.getMutedAccounts().isEmpty()) {
            return List.of();
        }
        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(profile.getMutedAccounts())),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    /** Mirrors the old Next.js reference project's own unmuteAccount — removes one entry
     * from the caller's own mutedAccounts. Same review-and-undo-only scope as
     * unblockAccount above: the reference itself never actually implemented a "mute this
     * user" trigger anywhere (grepped the whole app — only get/unmute exist there), so
     * mutedAccounts can currently only ever be emptied, never populated, through app code
     * on either stack. */
    public void unmuteAccount(String memberId, String mutedProfileId) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        if (profile.getMutedAccounts() != null) {
            profile.getMutedAccounts().removeIf(id -> id.toHexString().equals(mutedProfileId));
            profileRepository.save(profile);
        }
    }

    public List<CollectionSummaryResponse> getCollections(String profileId) {
        ObjectId creatorId = new ObjectId(profileId);
        return collectionRepository.findByCreatorOrderByCreatedAtAsc(creatorId).stream()
                .map(c -> new CollectionSummaryResponse(
                        c.getId(),
                        c.getName(),
                        c.getPublicStatus() != null ? c.getPublicStatus().name() : "PUBLIC",
                        c.getIsCustom() != null ? c.getIsCustom() : true,
                        c.getCards() != null ? c.getCards().size() : 0
                ))
                .collect(Collectors.toList());
    }

    /** Creates a new custom (isCustom: true) collection under the caller's own active
     * profile — same "reject a duplicate name for this creator" rule the old Next.js
     * reference's addProfileCollections applies before inserting. `memberId` (not a
     * profileId param) is deliberate: unlike the read-only endpoints above, a mutation needs
     * to resolve the creator from the authenticated caller itself, the same way
     * CardService.saveCard does, rather than trusting a client-supplied profileId. */
    public CollectionSummaryResponse createCollection(String memberId, CreateCollectionRequest request) {
        ObjectId creatorId = cardService.resolveActiveProfileId(memberId);

        String name = requireName(request);
        if (collectionRepository.findByCreatorAndName(creatorId, name).isPresent()) {
            throw new RuntimeException("Tab name already exists.");
        }
        Collection.PublicStatus publicStatus = parsePublicStatus(request);

        Collection collection = new Collection();
        collection.setName(name);
        collection.setCreator(creatorId);
        collection.setPublicStatus(publicStatus);
        collection.setIsCustom(true);
        collection.setCards(List.of());
        LocalDateTime now = LocalDateTime.now();
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        Collection saved = collectionRepository.save(collection);

        return new CollectionSummaryResponse(saved.getId(), saved.getName(), saved.getPublicStatus().name(), true, 0);
    }

    /** Renames a collection and/or flips its public/private status — same ownership check
     * the old Next.js reference's own updateProfileCollection applies (`_id` + `creator`
     * must both match), ported here as an explicit SecurityException instead of the
     * reference's silent "not found" (see CardService.authorizeCardMutation for the same
     * pattern elsewhere in this port). Deliberately no FLEXADMIN bypass, unlike card
     * mutations — the reference never extends collection ownership to admins either, and a
     * profile's own collections aren't the kind of content moderation is meant to reach. */
    public CollectionSummaryResponse updateCollection(String memberId, String collectionId, CreateCollectionRequest request) {
        ObjectId creatorId = cardService.resolveActiveProfileId(memberId);
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        if (!creatorId.equals(collection.getCreator())) {
            throw new SecurityException("You do not have permission to modify this collection");
        }

        String name = requireName(request);
        collectionRepository.findByCreatorAndName(creatorId, name)
                .filter(existing -> !existing.getId().equals(collectionId))
                .ifPresent(existing -> {
                    throw new RuntimeException("Tab name already exists.");
                });
        Collection.PublicStatus publicStatus = parsePublicStatus(request);

        collection.setName(name);
        collection.setPublicStatus(publicStatus);
        collection.setUpdatedAt(LocalDateTime.now());
        Collection saved = collectionRepository.save(collection);

        return new CollectionSummaryResponse(
                saved.getId(),
                saved.getName(),
                saved.getPublicStatus().name(),
                saved.getIsCustom() != null ? saved.getIsCustom() : true,
                saved.getCards() != null ? saved.getCards().size() : 0
        );
    }

    private static final int COLLECTION_NAME_MAX_LENGTH = 20;

    private String requireName(CreateCollectionRequest request) {
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new RuntimeException("Collection name is required");
        }
        if (name.length() > COLLECTION_NAME_MAX_LENGTH) {
            throw new RuntimeException("Collection name must be " + COLLECTION_NAME_MAX_LENGTH + " characters or fewer");
        }
        return name;
    }

    private Collection.PublicStatus parsePublicStatus(CreateCollectionRequest request) {
        try {
            return Collection.PublicStatus.valueOf(request.getPublicStatus());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid publicStatus");
        }
    }

    public List<CardSummaryResponse> getCollectionCards(String collectionId, String viewerProfileId) {
        Collection collection = collectionRepository.findById(collectionId).orElse(null);
        if (collection == null || collection.getCards() == null || collection.getCards().isEmpty()) {
            return List.of();
        }
        return cardService.fetchCardsByIds(collection.getCards(), viewerProfileId);
    }

    public CardPageResponse getPublishedCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchCardsByCreator(new ObjectId(profileId), true, page, limit, viewerProfileId);
    }

    /** See Settings > Report's own period dropdown (ReportSection.tsx) — either bound may
     * be null to leave that side open, same as CardService.fetchCardsByCreator's own
     * date-range overload. */
    public CardPageResponse getPublishedCards(
            String profileId, int page, int limit, String viewerProfileId,
            LocalDateTime startDate, LocalDateTime endDate
    ) {
        return cardService.fetchCardsByCreator(new ObjectId(profileId), true, page, limit, viewerProfileId, startDate, endDate);
    }

    public CardPageResponse getDraftCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchCardsByCreator(new ObjectId(profileId), false, page, limit, viewerProfileId);
    }

    public CardPageResponse getRecycleBinCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchDeletedCardsByCreator(new ObjectId(profileId), page, limit, viewerProfileId);
    }

    private int countPublishedCards(String profileId) {
        return countPublishedCards(profileId, null, null);
    }

    /** See getReportOverview's own date-range params — either bound may be null to leave
     * that side open. A single `.and("createdAt")` call, not two — see
     * CardService.fetchCardsByCreator's own doc comment on why. */
    private int countPublishedCards(String profileId, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = Criteria.where("creator").is(new ObjectId(profileId))
                .and("isReadyToPublish").is(true)
                .and("deleteInfo.isDeleted").ne(true);
        if (startDate != null && endDate != null) {
            criteria.and("createdAt").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            criteria.and("createdAt").gte(startDate);
        } else if (endDate != null) {
            criteria.and("createdAt").lte(endDate);
        }
        return (int) mongoTemplate.count(Query.query(criteria), "cards");
    }

    private int countDraftCards(String profileId) {
        return countDraftCards(profileId, null, null);
    }

    private int countDraftCards(String profileId, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = Criteria.where("creator").is(new ObjectId(profileId))
                .and("isReadyToPublish").ne(true)
                .and("deleteInfo.isDeleted").ne(true);
        if (startDate != null && endDate != null) {
            criteria.and("createdAt").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            criteria.and("createdAt").gte(startDate);
        } else if (endDate != null) {
            criteria.and("createdAt").lte(endDate);
        }
        return (int) mongoTemplate.count(Query.query(criteria), "cards");
    }

    /** Settings > Report's own "Overview" tab — see ReportOverviewResponse's own doc
     * comment on why there's no views stat here. Likes/comments are summed in Java rather
     * than a Mongo aggregation pipeline: a caller's own card count is small enough
     * (personal account, not a public feed query) that pulling every card's likes/comments
     * arrays and summing them here is simpler than an $unwind/$group pipeline for the same
     * result. */
    public ReportOverviewResponse getReportOverview(String memberId) {
        return getReportOverview(memberId, null, null);
    }

    /** See Settings > Report's own period dropdown (ReportSection.tsx) — either bound may
     * be null to leave that side open, same as every other date-range overload added
     * alongside this one. `followersCount` scopes to Profile.Follower's own `followedAt`
     * when a range is given; `followingCount` has no equivalent per-item timestamp on this
     * side (see Profile's own `following: List&lt;ObjectId&gt;`, no date attached to each
     * entry) so it's always the current total regardless of period. `totalLikes`/
     * `totalComments` are summed only from cards *created* within the range — an
     * approximation, since individual likes/comments aren't themselves timestamped in this
     * schema, so "likes received in this window" can't be computed exactly; a card from
     * outside the window that got new likes during it isn't reflected. */
    public ReportOverviewResponse getReportOverview(String memberId, LocalDateTime startDate, LocalDateTime endDate) {
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        int followersCount;
        if (profile.getFollowers() == null) {
            followersCount = 0;
        } else if (startDate == null && endDate == null) {
            followersCount = profile.getFollowers().size();
        } else {
            Date startD = startDate != null ? Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()) : null;
            Date endD = endDate != null ? Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()) : null;
            followersCount = (int) profile.getFollowers().stream()
                    .filter(f -> f.getFollowedAt() != null)
                    .filter(f -> startD == null || !f.getFollowedAt().before(startD))
                    .filter(f -> endD == null || !f.getFollowedAt().after(endD))
                    .count();
        }
        int followingCount = profile.getFollowing() != null ? profile.getFollowing().size() : 0;
        int cardCount = countPublishedCards(profile.getId(), startDate, endDate);
        int draftCount = countDraftCards(profile.getId(), startDate, endDate);

        Criteria cardsCriteria = Criteria.where("creator").is(profileId).and("deleteInfo.isDeleted").ne(true);
        if (startDate != null && endDate != null) {
            cardsCriteria.and("createdAt").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            cardsCriteria.and("createdAt").gte(startDate);
        } else if (endDate != null) {
            cardsCriteria.and("createdAt").lte(endDate);
        }
        List<Card> ownCards = mongoTemplate.find(Query.query(cardsCriteria), Card.class);
        int totalLikes = ownCards.stream().mapToInt(c -> c.getLikes() != null ? c.getLikes().size() : 0).sum();
        int totalComments = ownCards.stream().mapToInt(c -> c.getComments() != null ? c.getComments().size() : 0).sum();

        // Card ids fetched unscoped by date — card views are scoped by CardView's own
        // `viewedAt` (see countCardViews below), not by when the card itself was created.
        List<ObjectId> ownCardIds = mongoTemplate.findDistinct(
                Query.query(Criteria.where("creator").is(profileId).and("deleteInfo.isDeleted").ne(true)),
                "_id", Card.class, ObjectId.class
        );
        int profileViewsCount = countProfileViews(profileId, startDate, endDate);
        int cardViewsCount = countCardViews(ownCardIds, startDate, endDate);

        // Previous-period comparison (powers the Report screen's progress bars) — only
        // meaningful for a concrete, bounded period, so this is skipped (all zero) unless
        // both bounds are given. Mirrors the exact same number of days immediately before
        // the current period — e.g. selecting "last 7 days" compares against the 7 days
        // before that, a custom 10-day range compares against the 10 days before it.
        int previousCardCount = 0;
        int previousDraftCount = 0;
        int previousProfileViewsCount = 0;
        int previousCardViewsCount = 0;
        if (startDate != null && endDate != null) {
            long periodDays = ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate()) + 1;
            LocalDateTime previousEnd = startDate.toLocalDate().minusDays(1).atTime(LocalTime.MAX);
            LocalDateTime previousStart = previousEnd.toLocalDate().minusDays(periodDays - 1).atStartOfDay();

            previousCardCount = countPublishedCards(profile.getId(), previousStart, previousEnd);
            previousDraftCount = countDraftCards(profile.getId(), previousStart, previousEnd);
            previousProfileViewsCount = countProfileViews(profileId, previousStart, previousEnd);
            previousCardViewsCount = countCardViews(ownCardIds, previousStart, previousEnd);
        }

        return new ReportOverviewResponse(
                cardCount, draftCount, followersCount, followingCount, totalLikes, totalComments,
                profileViewsCount, cardViewsCount,
                previousCardCount, previousDraftCount, previousProfileViewsCount, previousCardViewsCount
        );
    }

    /** Indexed range query against the ProfileView collection (see recordProfileView) —
     * replaces the old in-memory filter over Profile.viewDetails now that views are no
     * longer stored as an embedded array on the Profile document itself. */
    private int countProfileViews(ObjectId profileId, LocalDateTime startDate, LocalDateTime endDate) {
        Criteria criteria = Criteria.where("profileId").is(profileId);
        if (startDate != null && endDate != null) {
            criteria.and("viewedAt")
                    .gte(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()))
                    .lte(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        } else if (startDate != null) {
            criteria.and("viewedAt").gte(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()));
        } else if (endDate != null) {
            criteria.and("viewedAt").lte(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        }
        return (int) mongoTemplate.count(Query.query(criteria), ProfileView.class);
    }

    /** Same idea as countProfileViews, scoped to a fixed set of card ids instead of a
     * single profile. */
    private int countCardViews(List<ObjectId> cardIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (cardIds.isEmpty()) return 0;
        Criteria criteria = Criteria.where("cardId").in(cardIds);
        if (startDate != null && endDate != null) {
            criteria.and("viewedAt")
                    .gte(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()))
                    .lte(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        } else if (startDate != null) {
            criteria.and("viewedAt").gte(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()));
        } else if (endDate != null) {
            criteria.and("viewedAt").lte(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        }
        return (int) mongoTemplate.count(Query.query(criteria), CardView.class);
    }

    /** Powers the Report screen's line graph — one point per day in [startDate, endDate]
     * (inclusive), 0-filled for days with no views. Both bounds are required: unlike
     * getReportOverview's all-time fallback, a graph has no meaningful "unbounded" axis. */
    public List<DailySeriesPointResponse> getProfileViewsSeries(
            String memberId, LocalDateTime startDate, LocalDateTime endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Date startD = Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant());
        Date endD = Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant());

        AggregationOperation match = ctx -> new Document("$match", new Document("profileId", profileId)
                .append("viewedAt", new Document("$gte", startD).append("$lte", endD)));
        String zoneId = ZoneId.systemDefault().getId();
        AggregationOperation group = ctx -> new Document("$group", new Document("_id",
                new Document("$dateToString", new Document("format", "%Y-%m-%d")
                        .append("date", "$viewedAt")
                        .append("timezone", zoneId)))
                .append("count", new Document("$sum", 1)));

        Aggregation aggregation = Aggregation.newAggregation(match, group);
        List<Document> results = mongoTemplate.aggregate(aggregation, "profileviews", Document.class).getMappedResults();

        Map<String, Integer> countsByDay = new HashMap<>();
        for (Document doc : results) {
            countsByDay.put(doc.getString("_id"), doc.getInteger("count"));
        }

        List<DailySeriesPointResponse> series = new ArrayList<>();
        LocalDate cursor = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();
        while (!cursor.isAfter(end)) {
            String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
            series.add(new DailySeriesPointResponse(key, countsByDay.getOrDefault(key, 0)));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /** Powers the Report screen's followers line graph — one point per day in
     * [startDate, endDate], each the *cumulative* follower total as of that day (not new
     * followers that day, unlike getProfileViewsSeries). Both bounds required, same as
     * getProfileViewsSeries.
     *
     * Caveat: this is built from Profile.Follower's own `followedAt`, but Follower entries
     * are removed outright on unfollow — there's no `unfollowedAt` kept anywhere in this
     * schema. So this is really "days since each *currently still following* follower
     * joined," not a true historical total; anyone who has since unfollowed disappears
     * from every day's count, including days they were actually a follower. Short of
     * adding an unfollow-event log (mirroring ProfileView/CardView), that's not
     * reconstructable from what's stored today. */
    public List<DailySeriesPointResponse> getFollowersSeries(String memberId, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        ObjectId profileId = cardService.resolveActiveProfileId(memberId);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        ZoneId zone = ZoneId.systemDefault();
        List<LocalDate> followedDates = profile.getFollowers() == null ? List.of() : profile.getFollowers().stream()
                .map(Profile.Follower::getFollowedAt)
                .filter(Objects::nonNull)
                .map(d -> d.toInstant().atZone(zone).toLocalDate())
                .sorted()
                .collect(Collectors.toList());

        LocalDate cursor = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();
        int idx = 0;
        int cumulative = 0;
        // Seed the running total with every follow that happened before the visible range
        // even starts, so the first plotted point is the real total on that day, not just
        // "new follows since the range began."
        while (idx < followedDates.size() && followedDates.get(idx).isBefore(cursor)) {
            idx++;
            cumulative++;
        }

        List<DailySeriesPointResponse> series = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            while (idx < followedDates.size() && !followedDates.get(idx).isAfter(cursor)) {
                idx++;
                cumulative++;
            }
            series.add(new DailySeriesPointResponse(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE), cumulative));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /** Ported from the old Next.js reference's updateProfileViewData — records that
     * `viewerProfileId` viewed `profileId`'s profile. No-ops on a self-view or a missing
     * viewer id, and dedups repeat views from the same viewer within a 3-minute window
     * (same anti-spam window as the reference) so refreshing/re-opening a profile doesn't
     * inflate the count. */
    public void recordProfileView(String profileId, String viewerProfileId) {
        if (profileId == null || viewerProfileId == null || profileId.equals(viewerProfileId)) {
            return;
        }
        if (!profileRepository.existsById(profileId)) {
            return;
        }

        ObjectId profileObjectId = new ObjectId(profileId);
        ObjectId viewerObjectId = new ObjectId(viewerProfileId);
        Date now = new Date();
        Date threeMinutesAgo = new Date(now.getTime() - 3 * 60 * 1000);

        Criteria recentCriteria = Criteria.where("profileId").is(profileObjectId)
                .and("viewerId").is(viewerObjectId)
                .and("viewedAt").gt(threeMinutesAgo);
        boolean recentView = mongoTemplate.exists(Query.query(recentCriteria), ProfileView.class);
        if (recentView) {
            return;
        }

        ProfileView view = new ProfileView();
        view.setProfileId(profileObjectId);
        view.setViewerId(viewerObjectId);
        view.setViewedAt(now);
        mongoTemplate.insert(view);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(profileObjectId)),
                new Update().inc("totalViews", 1),
                Profile.class
        );
    }
}

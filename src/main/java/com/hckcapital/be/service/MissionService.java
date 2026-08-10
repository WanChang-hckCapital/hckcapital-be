package com.hckcapital.be.service;

import com.hckcapital.be.dto.IncrementMissionResult;
import com.hckcapital.be.dto.MissionActionResponse;
import com.hckcapital.be.dto.MissionProgressResult;
import com.hckcapital.be.dto.MissionSlotResponse;
import com.hckcapital.be.dto.MissionsResponse;
import com.hckcapital.be.model.MissionProgress;
import com.hckcapital.be.model.PointsLog;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.repository.MissionProgressRepository;
import com.hckcapital.be.repository.PointsLogRepository;
import com.hckcapital.be.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ported from the old Next.js reference project's own lib/actions/mission.actions.ts +
 * lib/constants/mission-config.ts. Missions themselves are a hardcoded config (MISSION_CONFIG
 * below), not Mongo documents — only a profile's progress against each (period, missionType)
 * is persisted, in MissionProgress (see that model's own doc comment).
 *
 * Card like/comment/share/publish elsewhere in this backend call incrementProgress (or the
 * compound onCardShared/onCardCreated helpers) as a side effect — see each call site's own
 * doc comment (CardService.toggleLike/recordCardShare/publishCard,
 * CardCommentService.addComment) for exactly where. */
@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionProgressRepository missionProgressRepository;
    private final ProfileRepository profileRepository;
    private final PointsLogRepository pointsLogRepository;

    public static final String PERIOD_DAILY = "daily";
    public static final String PERIOD_WEEKLY = "weekly";

    public static final String TYPE_CHECK_IN = "check_in";
    public static final String TYPE_LIKE_CARDS = "like_cards";
    public static final String TYPE_LEAVE_COMMENTS = "leave_comments";
    public static final String TYPE_CREATE_CARD = "create_card";
    public static final String TYPE_CREATE_3_CARDS = "create_3_cards";
    public static final String TYPE_PRIME_TIME = "prime_time";
    public static final String TYPE_SHARE_CARD = "share_card";
    public static final String TYPE_WEEKLY_COMPLETION = "weekly_completion";

    private record MissionRule(int target, int points) {
    }

    // See onCardLiked/onCommentAdded's own doc comments — CardService.toggleLike /
    // CardCommentService.addComment each unpack this straight into their own response's two
    // matching fields (CardLikeToggleResponse.missionProgress/primeTimeMissionProgress,
    // CardCommentResponse's own same-named pair). `mission` is whichever single mission type
    // that action drives (LIKE_CARDS for a like, LEAVE_COMMENTS for a comment) — not a
    // per-action-specific field name, so this one record covers every "the user did one
    // thing, here's that thing's own progress plus prime-time's" hook.
    public record MissionAndPrimeTimeResult(IncrementMissionResult mission, IncrementMissionResult primeTime) {
    }

    // See onCardPublished's own doc comment — a card create/publish drives two missions at
    // once (CREATE_CARD + CREATE_3_CARDS), so needs a dedicated 3-field shape instead of
    // MissionAndPrimeTimeResult's two.
    public record CreateCardMissionResult(
            IncrementMissionResult createCard, IncrementMissionResult create3Cards, IncrementMissionResult primeTime) {
    }

    // Exact values from the reference's own MISSION_CONFIG (lib/constants/mission-config.ts)
    // — a LinkedHashMap so the Missions tab's own list renders in this same, deliberate
    // order rather than whatever order a HashMap happens to iterate in.
    private static final Map<String, Map<String, MissionRule>> MISSION_CONFIG = Map.of(
            PERIOD_DAILY, dailyConfig(),
            PERIOD_WEEKLY, weeklyConfig()
    );

    private static Map<String, MissionRule> dailyConfig() {
        Map<String, MissionRule> m = new LinkedHashMap<>();
        m.put(TYPE_CHECK_IN, new MissionRule(1, 10));
        m.put(TYPE_LIKE_CARDS, new MissionRule(3, 15));
        m.put(TYPE_LEAVE_COMMENTS, new MissionRule(3, 15));
        m.put(TYPE_CREATE_CARD, new MissionRule(1, 15));
        m.put(TYPE_CREATE_3_CARDS, new MissionRule(3, 40));
        m.put(TYPE_PRIME_TIME, new MissionRule(1, 30));
        m.put(TYPE_SHARE_CARD, new MissionRule(3, 15));
        return m;
    }

    private static Map<String, MissionRule> weeklyConfig() {
        Map<String, MissionRule> m = new LinkedHashMap<>();
        m.put(TYPE_CHECK_IN, new MissionRule(7, 100));
        m.put(TYPE_LIKE_CARDS, new MissionRule(15, 50));
        m.put(TYPE_LEAVE_COMMENTS, new MissionRule(15, 50));
        m.put(TYPE_CREATE_CARD, new MissionRule(10, 100));
        m.put(TYPE_PRIME_TIME, new MissionRule(5, 100));
        m.put(TYPE_SHARE_CARD, new MissionRule(10, 100));
        m.put(TYPE_WEEKLY_COMPLETION, new MissionRule(5, 50));
        return m;
    }

    /** UTC midnight today — the reference's own getTodayUTC(). */
    private Date todayUTC() {
        return Date.from(LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /** UTC midnight of this week's Monday — the reference's own getWeekStartUTC(). */
    private Date weekStartUTC() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        return Date.from(monday.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private Date periodStart(String period) {
        return PERIOD_WEEKLY.equals(period) ? weekStartUTC() : todayUTC();
    }

    /** The reference's own isPrimeTime() compares `new Date().getHours()` (server-local
     * time) against an 18–21 window while its sibling getTodayUTC()/getWeekStartUTC() both
     * correctly use UTC — a latent inconsistency in that codebase (its own doc comment
     * claims UTC). This port intentionally uses real UTC throughout instead of replicating
     * that mismatch, since "18:00–21:00 UTC" is what the feature's own copy documents. */
    public boolean isPrimeTime() {
        int hour = java.time.ZonedDateTime.now(ZoneOffset.UTC).getHour();
        return hour >= 18 && hour < 21;
    }

    private int bubblePoint(Profile profile) {
        return profile.getBubblePoint() != null ? profile.getBubblePoint() : 0;
    }

    private void logPoints(ObjectId profileId, int pointChanges, int before, int after,
                            ObjectId missionId, String description) {
        PointsLog log = new PointsLog();
        log.setProfileId(profileId);
        log.setPointChanges(pointChanges);
        log.setBeforePoints(before);
        log.setAfterPoints(after);
        log.setSourceType("mission");
        log.setMission(missionId);
        log.setDescription(description);
        Date now = new Date();
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        pointsLogRepository.save(log);
    }

    /** Lazily fetches-or-creates this profile's slot for every mission MISSION_CONFIG
     * declares for `period` — same "no init job needed" approach as the reference's own
     * getOrCreatePeriodSlots (there via bulkWrite($setOnInsert); here via a fetch-then-fill-
     * missing-with-save loop, since this backend has no existing bulk-upsert helper and the
     * slot count per period is small (≤7) — a per-missing-slot save is not a meaningful cost
     * here compared to one bulk call). */
    private Map<String, MissionProgress> getOrCreateSlots(ObjectId profileId, String period) {
        Map<String, MissionRule> config = MISSION_CONFIG.get(period);
        Date start = periodStart(period);

        List<MissionProgress> existing = missionProgressRepository
                .findByProfileIdAndPeriodAndPeriodStart(profileId, period, start);
        Map<String, MissionProgress> byType = new LinkedHashMap<>();
        for (MissionProgress p : existing) {
            byType.put(p.getMissionType(), p);
        }

        for (String missionType : config.keySet()) {
            if (byType.containsKey(missionType)) continue;
            MissionProgress slot = new MissionProgress();
            slot.setProfileId(profileId);
            slot.setPeriod(period);
            slot.setPeriodStart(start);
            slot.setMissionType(missionType);
            slot.setProgress(0);
            slot.setCompleted(false);
            slot.setRewardClaimed(false);
            Date now = new Date();
            slot.setCreatedAt(now);
            slot.setUpdatedAt(now);
            slot = missionProgressRepository.save(slot);
            byType.put(missionType, slot);
        }
        return byType;
    }

    public MissionsResponse getMissions(ObjectId profileId, String period) {
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        Map<String, MissionRule> config = MISSION_CONFIG.get(period);
        Map<String, MissionProgress> slots = getOrCreateSlots(profileId, period);

        List<MissionSlotResponse> missions = config.entrySet().stream()
                .map(entry -> {
                    MissionProgress slot = slots.get(entry.getKey());
                    MissionRule rule = entry.getValue();
                    return new MissionSlotResponse(
                            entry.getKey(), period, slot.getProgress(), rule.target(), rule.points(),
                            Boolean.TRUE.equals(slot.getCompleted()), Boolean.TRUE.equals(slot.getRewardClaimed()));
                })
                .toList();

        return new MissionsResponse(bubblePoint(profile), missions);
    }

    /** See MissionSlotResponse's own doc comment on why check-in is a distinct endpoint
     * from claimReward: it both grants the daily reward immediately (no separate "complete"
     * step the way like/comment/share/create-card have) AND bumps the weekly CHECK_IN
     * mission's own progress by 1 — mirrors the reference's own claimCheckIn exactly. */
    public MissionActionResponse checkIn(ObjectId profileId) {
        Date dailyStart = todayUTC();
        MissionProgress dailySlot = missionProgressRepository
                .findByProfileIdAndPeriodAndPeriodStartAndMissionType(profileId, PERIOD_DAILY, dailyStart, TYPE_CHECK_IN)
                .orElse(null);
        if (dailySlot != null && Boolean.TRUE.equals(dailySlot.getRewardClaimed())) {
            return new MissionActionResponse(false, "Already checked in today.", null);
        }

        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        int points = MISSION_CONFIG.get(PERIOD_DAILY).get(TYPE_CHECK_IN).points();
        int before = bubblePoint(profile);
        int after = before + points;
        profile.setBubblePoint(after);
        profileRepository.save(profile);

        Date now = new Date();
        if (dailySlot == null) {
            dailySlot = new MissionProgress();
            dailySlot.setProfileId(profileId);
            dailySlot.setPeriod(PERIOD_DAILY);
            dailySlot.setPeriodStart(dailyStart);
            dailySlot.setMissionType(TYPE_CHECK_IN);
            dailySlot.setCreatedAt(now);
        }
        dailySlot.setProgress(1);
        dailySlot.setCompleted(true);
        dailySlot.setRewardClaimed(true);
        dailySlot.setUpdatedAt(now);
        dailySlot = missionProgressRepository.save(dailySlot);

        logPoints(profileId, points, before, after, new ObjectId(dailySlot.getId()), "Daily check-in reward.");

        // Also bumps (but does not auto-claim) the weekly CHECK_IN slot's own progress —
        // matching the reference exactly.
        bumpWeeklyProgressOnly(profileId, TYPE_CHECK_IN);

        return new MissionActionResponse(true, null, after);
    }

    private void bumpWeeklyProgressOnly(ObjectId profileId, String missionType) {
        MissionRule weeklyRule = MISSION_CONFIG.get(PERIOD_WEEKLY).get(missionType);
        if (weeklyRule == null) return;
        Date weekStart = weekStartUTC();
        MissionProgress slot = missionProgressRepository
                .findByProfileIdAndPeriodAndPeriodStartAndMissionType(profileId, PERIOD_WEEKLY, weekStart, missionType)
                .orElse(null);
        if (slot != null && Boolean.TRUE.equals(slot.getCompleted())) return;

        Date now = new Date();
        if (slot == null) {
            slot = new MissionProgress();
            slot.setProfileId(profileId);
            slot.setPeriod(PERIOD_WEEKLY);
            slot.setPeriodStart(weekStart);
            slot.setMissionType(missionType);
            slot.setProgress(0);
            slot.setCreatedAt(now);
        }
        int newProgress = Math.min((slot.getProgress() != null ? slot.getProgress() : 0) + 1, weeklyRule.target());
        slot.setProgress(newProgress);
        slot.setCompleted(newProgress >= weeklyRule.target());
        slot.setUpdatedAt(now);
        missionProgressRepository.save(slot);
    }

    /** Safety-net claim for a mission that's already `completed` but not yet
     * `rewardClaimed` — in this backend (like the reference), every progress-driving mission
     * auto-grants the instant it completes (see incrementProgress below), so this path
     * should rarely actually fire; it exists because the reference's own UI still renders a
     * claim affordance for that state and this backend mirrors the same endpoint shape. */
    public MissionActionResponse claimReward(ObjectId profileId, String missionType, String period) {
        Date start = periodStart(period);
        MissionProgress slot = missionProgressRepository
                .findByProfileIdAndPeriodAndPeriodStartAndMissionType(profileId, period, start, missionType)
                .orElse(null);
        if (slot == null || !Boolean.TRUE.equals(slot.getCompleted())) {
            return new MissionActionResponse(false, "Mission is not completed yet.", null);
        }
        if (Boolean.TRUE.equals(slot.getRewardClaimed())) {
            return new MissionActionResponse(false, "Reward already claimed.", null);
        }

        MissionRule rule = MISSION_CONFIG.get(period).get(missionType);
        Profile profile = profileRepository.findById(profileId.toHexString())
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        int before = bubblePoint(profile);
        int after = before + rule.points();
        profile.setBubblePoint(after);
        profileRepository.save(profile);

        slot.setRewardClaimed(true);
        slot.setUpdatedAt(new Date());
        missionProgressRepository.save(slot);

        logPoints(profileId, rule.points(), before, after, new ObjectId(slot.getId()),
                period + " mission reward: " + missionType + ".");

        if (PERIOD_WEEKLY.equals(period) && !TYPE_WEEKLY_COMPLETION.equals(missionType)) {
            syncWeeklyCompletion(profileId);
        }

        return new MissionActionResponse(true, null, after);
    }

    /** Bumps progress for `missionType` in both the daily and weekly slot (whichever the
     * config actually defines it for — e.g. CREATE_3_CARDS has no weekly entry, so only the
     * daily side moves), auto-granting the reward the instant a slot reaches its target.
     * Mirrors the reference's own incrementMissionProgress, minus its concurrent-completion
     * guard (`findOneAndUpdate({completed:false}, ...)`) — this backend doesn't yet have a
     * conditional-update helper wired up the same way, so a genuinely simultaneous double
     * request from the same profile could in principle double-grant; low-risk enough for a
     * first port (a single user's own like/comment/share/create-card actions are naturally
     * sequential from the RN app) to not block on, but worth hardening later with an atomic
     * `findAndModify` if abuse shows up. */
    public IncrementMissionResult incrementProgress(ObjectId profileId, String missionType) {
        MissionProgressResult dailyResult = null;
        MissionProgressResult weeklyResult = null;

        for (String period : List.of(PERIOD_DAILY, PERIOD_WEEKLY)) {
            MissionRule rule = MISSION_CONFIG.get(period).get(missionType);
            if (rule == null) continue;

            Date start = periodStart(period);
            MissionProgress slot = missionProgressRepository
                    .findByProfileIdAndPeriodAndPeriodStartAndMissionType(profileId, period, start, missionType)
                    .orElse(null);
            // Already completed before this call — a no-op, same as the reference's own
            // incrementMissionProgress (no re-grant, and no toast for it either, since
            // nothing actually changed this time).
            if (slot != null && Boolean.TRUE.equals(slot.getCompleted())) continue;

            Date now = new Date();
            if (slot == null) {
                slot = new MissionProgress();
                slot.setProfileId(profileId);
                slot.setPeriod(period);
                slot.setPeriodStart(start);
                slot.setMissionType(missionType);
                slot.setProgress(0);
                slot.setCreatedAt(now);
            }
            int newProgress = Math.min((slot.getProgress() != null ? slot.getProgress() : 0) + 1, rule.target());
            boolean justCompleted = newProgress >= rule.target();
            slot.setProgress(newProgress);
            slot.setCompleted(justCompleted);
            slot.setUpdatedAt(now);

            if (justCompleted) {
                slot.setRewardClaimed(true);
                slot = missionProgressRepository.save(slot);

                Profile profile = profileRepository.findById(profileId.toHexString()).orElse(null);
                if (profile != null) {
                    int before = bubblePoint(profile);
                    int after = before + rule.points();
                    profile.setBubblePoint(after);
                    profileRepository.save(profile);
                    logPoints(profileId, rule.points(), before, after, new ObjectId(slot.getId()),
                            period + " mission auto-completed: " + missionType + ".");
                }

                if (PERIOD_WEEKLY.equals(period) && !TYPE_WEEKLY_COMPLETION.equals(missionType)) {
                    syncWeeklyCompletion(profileId);
                }
            } else {
                missionProgressRepository.save(slot);
            }

            MissionProgressResult result = new MissionProgressResult(newProgress, rule.target(), justCompleted, rule.points());
            if (PERIOD_DAILY.equals(period)) dailyResult = result;
            else weeklyResult = result;
        }

        return new IncrementMissionResult(dailyResult, weeklyResult);
    }

    /** The WEEKLY_COMPLETION meta-mission — auto-unlocks once 5 of the 6 real weekly
     * missions have been claimed this week. Mirrors the reference's own (internal, not
     * exported) syncWeeklyCompletion. */
    private void syncWeeklyCompletion(ObjectId profileId) {
        MissionRule metaRule = MISSION_CONFIG.get(PERIOD_WEEKLY).get(TYPE_WEEKLY_COMPLETION);
        Date weekStart = weekStartUTC();
        long claimedCount = missionProgressRepository
                .countByProfileIdAndPeriodAndPeriodStartAndMissionTypeNotAndRewardClaimedTrue(
                        profileId, PERIOD_WEEKLY, weekStart, TYPE_WEEKLY_COMPLETION);

        MissionProgress slot = missionProgressRepository
                .findByProfileIdAndPeriodAndPeriodStartAndMissionType(profileId, PERIOD_WEEKLY, weekStart, TYPE_WEEKLY_COMPLETION)
                .orElse(null);
        Date now = new Date();
        if (slot == null) {
            slot = new MissionProgress();
            slot.setProfileId(profileId);
            slot.setPeriod(PERIOD_WEEKLY);
            slot.setPeriodStart(weekStart);
            slot.setMissionType(TYPE_WEEKLY_COMPLETION);
            slot.setCreatedAt(now);
        }
        boolean alreadyClaimed = Boolean.TRUE.equals(slot.getRewardClaimed());
        slot.setProgress((int) Math.min(claimedCount, metaRule.target()));
        slot.setCompleted(claimedCount >= metaRule.target());
        slot.setUpdatedAt(now);

        if (!alreadyClaimed && Boolean.TRUE.equals(slot.getCompleted())) {
            slot.setRewardClaimed(true);
            slot = missionProgressRepository.save(slot);

            Profile profile = profileRepository.findById(profileId.toHexString()).orElse(null);
            if (profile != null) {
                int before = bubblePoint(profile);
                int after = before + metaRule.points();
                profile.setBubblePoint(after);
                profileRepository.save(profile);
                logPoints(profileId, metaRule.points(), before, after, new ObjectId(slot.getId()),
                        "Weekly completion bonus.");
            }
        } else {
            missionProgressRepository.save(slot);
        }
    }

    // --- Compound hooks called from the actual user-action call sites ------------------

    /** See CardService.toggleLike's own call site — the two results here feed straight into
     * CardLikeToggleResponse.missionProgress/primeTimeMissionProgress, which the RN app's
     * MissionProgressToast renders without a second request, same as the reference's own
     * updateCardLikes → CardActions.tsx → useMissionToastQueue.enqueue flow. */
    public MissionAndPrimeTimeResult onCardLiked(ObjectId profileId) {
        IncrementMissionResult likeCards = incrementProgress(profileId, TYPE_LIKE_CARDS);
        IncrementMissionResult primeTime = isPrimeTime() ? incrementProgress(profileId, TYPE_PRIME_TIME) : null;
        return new MissionAndPrimeTimeResult(likeCards, primeTime);
    }

    /** See CardCommentService.addComment's own call site — same "feeds straight into the
     * response, no second request" pattern as onCardLiked above. */
    public MissionAndPrimeTimeResult onCommentAdded(ObjectId profileId) {
        IncrementMissionResult leaveComments = incrementProgress(profileId, TYPE_LEAVE_COMMENTS);
        IncrementMissionResult primeTime = isPrimeTime() ? incrementProgress(profileId, TYPE_PRIME_TIME) : null;
        return new MissionAndPrimeTimeResult(leaveComments, primeTime);
    }

    /** See CardService.recordCardShare's own call site — same "feeds straight into the
     * response, no second request" pattern as onCardLiked/onCommentAdded above. */
    public MissionAndPrimeTimeResult onCardShared(ObjectId profileId) {
        IncrementMissionResult shareCard = incrementProgress(profileId, TYPE_SHARE_CARD);
        IncrementMissionResult primeTime = isPrimeTime() ? incrementProgress(profileId, TYPE_PRIME_TIME) : null;
        return new MissionAndPrimeTimeResult(shareCard, primeTime);
    }

    /** Unlike the other hooks above, publishing/creating a card drives *two* missions at
     * once (CREATE_CARD and CREATE_3_CARDS both track it, at different targets — see
     * MISSION_CONFIG), so this needs its own 3-field result rather than
     * MissionAndPrimeTimeResult's two. See CardService.saveCard/publishCard's own call
     * sites for where each of these three feeds into a response. */
    public CreateCardMissionResult onCardPublished(ObjectId profileId) {
        IncrementMissionResult createCard = incrementProgress(profileId, TYPE_CREATE_CARD);
        IncrementMissionResult create3Cards = incrementProgress(profileId, TYPE_CREATE_3_CARDS);
        IncrementMissionResult primeTime = isPrimeTime() ? incrementProgress(profileId, TYPE_PRIME_TIME) : null;
        return new CreateCardMissionResult(createCard, create3Cards, primeTime);
    }
}

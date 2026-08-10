package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** Mirrors the old Next.js reference project's own lib/models/mission-progress.ts field-for-
 * field — same MongoDB collection ("missionprogresses", Mongoose's default pluralized name).
 * One row per (profile, period, periodStart, missionType) — the compound unique index the
 * reference declares on that same tuple (see MissionService.ensureUniqueIndex) is what makes
 * the "upsert-or-fetch" pattern (MissionService.getOrCreateSlot) safe to call repeatedly.
 *
 * `period`/`missionType` are plain Strings, not Java enums, matching how ReferralHistory.
 * status is kept as a String elsewhere in this codebase — see MissionService's own
 * MissionPeriod/MissionType constants for the actual value sets ("daily"/"weekly" and
 * "check_in"/"like_cards"/etc.). `periodStart` is always UTC midnight (daily) or UTC Monday
 * midnight (weekly) — see MissionService.periodStart's own doc comment for why no scheduled
 * reset job is needed: rollover just means a new periodStart value, so old rows are simply
 * never queried again rather than being reset in place. */
@Data
@Document(collection = "missionprogresses")
public class MissionProgress {
    @Id
    private String id;

    private ObjectId profileId;

    private String period;

    private Date periodStart;

    private String missionType;

    private Integer progress = 0;

    private Boolean completed = false;

    private Boolean rewardClaimed = false;

    private Date createdAt;

    private Date updatedAt;
}

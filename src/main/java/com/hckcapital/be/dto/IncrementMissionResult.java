package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Mirrors the old Next.js reference project's own IncrementMissionResult (lib/actions/
 * mission.actions.ts) — the daily and weekly slot results of one incrementProgress call for
 * a single missionType. Either side is null when that period doesn't define the mission at
 * all (e.g. CREATE_3_CARDS has no weekly entry) or the slot was already completed before
 * this call (a no-op — see MissionService.incrementProgress's own doc comment on why no
 * toast should show for that case). */
@Data
@AllArgsConstructor
public class IncrementMissionResult {
    private MissionProgressResult daily;
    private MissionProgressResult weekly;
}

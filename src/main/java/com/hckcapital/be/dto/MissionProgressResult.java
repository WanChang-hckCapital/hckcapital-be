package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One period's worth of MissionService.incrementProgress's own return value — mirrors the
 * old Next.js reference project's own MissionProgressResult (lib/actions/mission.actions.ts):
 * `{progress, target, completed, points}` for a single (period, missionType) slot right
 * after it was bumped by 1. Used to drive CardActions.tsx's own MissionProgressToast without
 * a second round trip — the like/comment/share/publish response embeds this directly. */
@Data
@AllArgsConstructor
public class MissionProgressResult {
    private int progress;
    private int target;
    private boolean completed;
    private int points;
}

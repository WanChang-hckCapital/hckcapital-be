package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** `missionProgress`/`primeTimeMissionProgress` are only populated when the caller's
 * identity was resolvable (see CardController.recordCardShare's own best-effort auth
 * handling) — same "feeds the RN app's MissionProgressToast without a second request"
 * pattern as CardLikeToggleResponse/CardCommentResponse. */
@Data
@AllArgsConstructor
public class CardShareResponse {
    private int shareCount;
    private IncrementMissionResult missionProgress;
    private IncrementMissionResult primeTimeMissionProgress;
}

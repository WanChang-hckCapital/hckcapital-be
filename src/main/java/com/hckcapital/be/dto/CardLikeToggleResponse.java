package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** `missionProgress`/`primeTimeMissionProgress` are only ever populated on an actual new
 * like (never on unlike, never on a repeat like of a card already credited — see
 * CardService.toggleLike's own anti-farming guard) — mirrors the reference's own
 * updateCardLikes response shape (`{missionProgress, primeTimeMissionProgress}`), which
 * CardActions.tsx enqueues straight into its MissionProgressToast without a second request. */
@Data
@AllArgsConstructor
public class CardLikeToggleResponse {
    private boolean liked;
    private int likeCount;
    private IncrementMissionResult missionProgress;
    private IncrementMissionResult primeTimeMissionProgress;
}

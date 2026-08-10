package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** The three mission fields are only populated on an actually-new, actually-published card
 * (see CardService.saveCard's own doc comment on that exact condition) — null on every
 * update to an existing card, and null on a new card saved as a draft
 * (isReadyToPublish: false). Same "feeds the RN app's MissionProgressToast without a second
 * request" pattern as CardLikeToggleResponse/CardCommentResponse/CardShareResponse. */
@Data
@AllArgsConstructor
public class SaveCardResponse {
    private String cardId;
    private IncrementMissionResult createCardMissionProgress;
    private IncrementMissionResult create3CardsMissionProgress;
    private IncrementMissionResult primeTimeMissionProgress;
}

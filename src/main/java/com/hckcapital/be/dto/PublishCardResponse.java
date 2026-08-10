package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /{cardId}/publish — see CardService.publishCard. Same three mission fields as
 * SaveCardResponse's own (this is the "publish a card that was saved as a draft earlier"
 * path, the other place onCardPublished's own CreateCardMissionResult can come from). */
@Data
@AllArgsConstructor
public class PublishCardResponse {
    private IncrementMissionResult createCardMissionProgress;
    private IncrementMissionResult create3CardsMissionProgress;
    private IncrementMissionResult primeTimeMissionProgress;
}

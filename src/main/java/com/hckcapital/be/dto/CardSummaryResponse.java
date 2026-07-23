package com.hckcapital.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CardSummaryResponse {
    private String cardId;
    private String title;
    private String description;
    private String creatorId;
    private String creatorAccountName;
    private int likes;
    private int comments;
    private int shareCount;
    private String cardShareTitle;
    /** Full share-title list (cardShareTitle only exposes the first entry, kept as-is for
     * existing callers) — needed to reload all of them into the editor for further editing. */
    private List<String> cardShareTitles;
    private String html;
    private String lineComponentsJson;
    /** Raw editor tree JSON (Card.components' content) — the round-trippable source the RN
     * editor reloads into itself when a card is reopened for editing (see EditCardScreen).
     * Only populated by fetchCardById; the list endpoints don't look this up (unneeded
     * payload weight for a feed). */
    private String editorJson;
    private String creatorImage;
    @JsonProperty("isLikedByMe")
    private boolean isLikedByMe;
}

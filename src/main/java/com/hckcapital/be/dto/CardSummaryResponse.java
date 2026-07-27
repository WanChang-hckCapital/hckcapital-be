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
    /** Only populated by fetchCardById (see CardService.mapDocument) — the list endpoints
     * don't need it and skip the extra $project field. Drives CardDetailScreen.tsx's own
     * creator-only "Publish" button: a card lands here as a draft (isReadyToPublish: false)
     * from e.g. the Shortcut feature, and the creator flips it once they're happy with it. */
    @JsonProperty("isReadyToPublish")
    private boolean isReadyToPublish;
}

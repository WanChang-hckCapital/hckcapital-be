package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveCardRequest {

    /** Mongo _id of an existing card to update — omit/null to create a new card. */
    private String cardId;

    @NotBlank
    private String title;

    private String description;

    private List<String> categories;

    private List<String> cardShareTitle;

    /** Raw editor tree JSON (CardEditorContainer) — the round-trippable source, reloaded
     * into the editor when the card is reopened for further editing. */
    @NotBlank
    private String editorJson;

    /** Spec-compliant LINE Flex Message JSON — what's actually sent to LINE. */
    @NotBlank
    private String lineComponentsJson;

    /** Rendered HTML preview. */
    @NotBlank
    private String html;

    /** Null (the default, from the regular card editor's own Save/Publish) means "ready to
     * publish" — CardService.saveCard treats a missing value as `true`, matching every
     * existing caller's behavior before this field existed. Explicitly `false` is how the
     * Shortcut feature (see MetadataController's own doc comment) saves its
     * auto-generated card as a draft the user can find under the Draft tab and finish
     * later, rather than immediately publishing something they never reviewed. */
    private Boolean isReadyToPublish;
}

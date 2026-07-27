package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body shape for both POST /profile/collections (create) and PUT
 * /profile/collections/{collectionId} (rename/re-visibility) — see
 * ProfileService.createCollection/updateCollection. Both actions need exactly the same two
 * fields, so one request DTO covers both rather than a near-duplicate second class. */
@Data
public class CreateCollectionRequest {

    /** @Size is a first-line guard only — ProfileService.requireName re-checks the
     * *trimmed* length itself so a validation failure always comes back through the
     * service's own RuntimeException -> {"error": ...} shape (same friendly-message
     * convention as its blank-name check) rather than Spring's default @Valid error body. */
    @NotBlank
    @Size(max = 20)
    private String name;

    /** "PUBLIC" or "PRIVATE" — validated/parsed against Collection.PublicStatus. */
    @NotBlank
    private String publicStatus;
}

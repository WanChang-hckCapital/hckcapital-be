package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** See ProfileService.updateProfileDetails — Settings > Manage's own edit form. Mirrors
 * OnboardRequest's shape minus country/phone (those belong to the one-time onboarding step,
 * not repeated profile edits) and minus email (Member.email is set at signup, never
 * re-collected here, same reasoning as OnboardRequest's own doc comment). */
@Data
public class UpdateProfileDetailsRequest {

    @NotBlank
    @Size(max = 30)
    private String accountname;

    @Size(max = 200)
    private String shortdescription;

    /** Relative GCS object path (see ImageUploadService.UploadResult's own doc comment) —
     * omitted/null when the user didn't change their photo in this edit. */
    private String imageFilePath;
}

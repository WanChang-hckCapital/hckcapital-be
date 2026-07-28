package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** See ProfileService.completeOnboarding — mirrors the old Next.js reference project's own
 * OnboardingComponent.tsx form (accountname/image/country/phone/shortdescription), minus
 * its email field (read-only there anyway — this backend already has the email from the
 * JWT-resolved Member, no need for the client to round-trip it back). */
@Data
public class OnboardRequest {

    @NotBlank
    @Size(max = 30)
    private String accountname;

    @Size(max = 200)
    private String shortdescription;

    /** Relative GCS object path (see ImageUploadService.UploadResult's own doc comment) —
     * not a full URL, matching every other Profile.imageFilePath in this app. */
    private String imageFilePath;

    private String country;

    private String countrycode;

    /** Already combined with the dial code client-side (see OnboardingScreen.tsx), same as
     * the reference's own fullPhoneNumber — this backend doesn't need to know the dial code
     * separately from countrycode. */
    private String phone;
}

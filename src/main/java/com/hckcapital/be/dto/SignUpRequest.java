package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequest {

    @NotBlank
    private String email;

    // Same shape as the old Next.js reference project's own sign-up zod schema (see
    // hckcapital/lib/validations/sign-up.ts): letters/numbers/underscore/dot only.
    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9._]+$")
    private String username;

    @NotBlank
    @Size(min = 8, max = 30)
    private String password;

    /** Confirm-password matching is a client-side-only check in the reference app too — this
     * backend never receives or compares it, same as that app's own createUser server
     * action signature (email, username, password, refCode — no confirmPassword param). */

    /** Optional — see AuthService.signup's own doc comment for the redemption logic this
     * feeds. An unknown/invalid code (no Profile.referralCode match) is silently ignored,
     * same as the reference's own createUser (logs a warning, doesn't fail the signup). */
    private String refCode;
}

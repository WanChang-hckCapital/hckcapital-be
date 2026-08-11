package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    /** The ID token returned by Google after the RN app's own OAuth flow completes (see
     * useGoogleSignIn.ts) — verified server-side in AuthService.loginWithGoogle before
     * anything in it is trusted. */
    @NotBlank
    private String idToken;

    /** Optional — see AuthService.loginWithGoogle's own doc comment. Only has any effect
     * the first time this Google account signs in (i.e. when it creates a brand-new
     * Member/Profile); ignored on every subsequent login by an already-existing account,
     * same as SignUpRequest.getRefCode's own semantics for email/password signup. */
    private String refCode;
}

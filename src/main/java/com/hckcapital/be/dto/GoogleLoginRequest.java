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
}

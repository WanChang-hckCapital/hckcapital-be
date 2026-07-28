package com.hckcapital.be.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank
    private String email;

    /** "en" or "zh-TW" — picks the email template's language (see AuthService.forgotPassword).
     * Optional: falls back the same way the old Next.js reference's own route did when
     * missing/unrecognized. */
    private String lang;
}

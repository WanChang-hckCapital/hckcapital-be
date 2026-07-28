package com.hckcapital.be.controller;

import com.hckcapital.be.dto.SendOtpRequest;
import com.hckcapital.be.dto.VerifyOtpRequest;
import com.hckcapital.be.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Same routes as the old Next.js reference project's own OTP endpoints (see
 * hckcapital/app/api/v1/otp/email/route.ts and .../otp/verify/route.ts) — used by the RN
 * app's sign-up flow (see AuthController.signup) to verify the user owns the email before
 * an account is created. There's no separate "resend" endpoint here either, matching the
 * reference: calling /otp/email again re-sends a fresh code (see OtpService.sendOtp). */
@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/email")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        try {
            otpService.sendOtp(request.getEmail());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            int status = "Too many attempts".equals(e.getMessage()) ? 429 : 500;
            return ResponseEntity.status(status).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            otpService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(Map.of("valid", true));
        } catch (RuntimeException e) {
            int status = "Too many incorrect attempts".equals(e.getMessage()) ? 429 : 200;
            return ResponseEntity.status(status).body(Map.of("valid", false, "error", e.getMessage()));
        }
    }
}

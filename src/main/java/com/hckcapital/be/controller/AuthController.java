package com.hckcapital.be.controller;

import com.hckcapital.be.dto.ForgotPasswordRequest;
import com.hckcapital.be.dto.GoogleLoginRequest;
import com.hckcapital.be.dto.LoginRequest;
import com.hckcapital.be.dto.LoginResponse;
import com.hckcapital.be.dto.SignUpRequest;
import com.hckcapital.be.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        try {
            LoginResponse response = authService.loginWithGoogle(request.getIdToken(), request.getRefCode());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            // Same generic response whether or not that email actually has an account (see
            // AuthService.forgotPassword's own doc comment) — deliberately doesn't say
            // "sent" outright, so this response itself can't be used to enumerate emails.
            authService.forgotPassword(request.getEmail(), request.getLang());
            return ResponseEntity.ok(Map.of("success", true, "message", "If an account exists for this email, a reset link has been sent."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // See AuthService.hasPassword — Settings > Forget Password's own gate for OAuth-only accounts.
    @GetMapping("/password-status")
    public ResponseEntity<?> getPasswordStatus(Authentication authentication) {
        String memberId = (String) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("hasPassword", authService.hasPassword(memberId)));
    }

    // Requires OtpController's own POST /api/v1/otp/verify to have already succeeded for
    // this email — see AuthService.signup's own doc comment and OtpService.requireVerified.
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request) {
        try {
            LoginResponse response = authService.signup(request.getEmail(), request.getUsername(), request.getPassword(), request.getRefCode());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}

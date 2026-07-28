package com.hckcapital.be.service;

import com.hckcapital.be.model.Otp;
import com.hckcapital.be.repository.OtpRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/** Email OTP verification, used by the sign-up flow (see AuthController.signup) to confirm
 * the user actually owns the email before an account is created. Ported from the old
 * Next.js reference project's own POST /api/v1/otp/email and POST /api/v1/otp/verify routes
 * (see hckcapital/app/api/v1/otp/email/route.ts and .../otp/verify/route.ts) — same "otps"
 * Mongo collection, same 6-digit/15-minute/5-attempt shape, except the code itself is
 * generated with SecureRandom rather than the reference's Math.random() (not
 * cryptographically strong — fine for a UI nicety, not for a value guarding account
 * creation). */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long EXPIRY_MINUTES = 15;

    private final OtpRepository otpRepository;
    private final SesEmailService sesEmailService;
    private final MongoTemplate mongoTemplate;

    @Value("${aws.otp-ses-address:}")
    private String otpSesAddress;

    // application.properties sets spring.data.mongodb.auto-index-creation=false
    // project-wide, so Otp.expiresAt's own @Indexed(expireAfterSeconds) annotation is
    // otherwise inert — create the TTL index explicitly here instead.
    @PostConstruct
    private void ensureExpiryIndex() {
        IndexOperations indexOps = mongoTemplate.indexOps(Otp.class);
        indexOps.createIndex(new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                .expire(0, TimeUnit.SECONDS));
    }

    public void sendOtp(String email) {
        if (otpSesAddress.isBlank()) {
            throw new RuntimeException("SES configuration missing");
        }

        Otp otp = otpRepository.findByEmail(email).orElseGet(Otp::new);
        if (otp.getId() != null && otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new RuntimeException("Too many attempts");
        }

        SecureRandom random = new SecureRandom();
        String code = String.format("%06d", random.nextInt(1_000_000));

        otp.setEmail(email);
        otp.setCode(code);
        otp.setSentAt(LocalDateTime.now());
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        otp.setDoneVerification(false);
        otp.setAttempts(otp.getAttempts() + 1);
        otpRepository.save(otp);

        try {
            sesEmailService.sendText(otpSesAddress, email, "Your Verification Code",
                    "Your verification code is: " + code + "\nThis code is valid for 15 minutes.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email");
        }
    }

    /** Throws with the same messages the reference's own verify route returns, on failure.
     *
     * Unlike the reference (which deletes the OTP doc immediately on success and otherwise
     * never uses its own doneVerification field), this marks the document verified instead
     * of deleting it — AuthService.signup checks that flag before creating an account, so
     * "verified" is actually enforced server-side rather than merely trusted from client
     * call ordering, which was a real gap in the original: nothing stopped the reference's
     * own createUser from being called without ever verifying an OTP at all. */
    public void verifyOtp(String email, String code) {
        Otp otp = otpRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("No OTP found"));

        if (otp.getExpiresAt() != null && otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.deleteByEmail(email);
            throw new RuntimeException("OTP expired");
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new RuntimeException("Too many incorrect attempts");
        }

        if (!otp.getCode().equals(code)) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            int remaining = MAX_ATTEMPTS - otp.getAttempts();
            throw new RuntimeException("Incorrect OTP. " + Math.max(remaining, 0) + " attempts remaining.");
        }

        otp.setDoneVerification(true);
        otpRepository.save(otp);
    }

    /** Used by AuthService.signup right before creating the account — see verifyOtp's own
     * doc comment. Deletes the OTP document once consumed (single-use, same as the
     * reference's own delete-on-verify, just moved to this later point). */
    public void requireVerified(String email) {
        Otp otp = otpRepository.findByEmail(email).orElse(null);
        if (otp == null || !otp.isDoneVerification()) {
            throw new RuntimeException("Email is not verified");
        }
        if (otp.getExpiresAt() != null && otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.deleteByEmail(email);
            throw new RuntimeException("Email verification has expired");
        }
        otpRepository.deleteByEmail(email);
    }
}

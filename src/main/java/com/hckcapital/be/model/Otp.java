package com.hckcapital.be.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Mirrors the old Next.js reference project's own Otp model (see hckcapital/lib/models/
 * otp.ts) field-for-field, same "otps" collection (Mongoose's default pluralized name) —
 * used for the sign-up email-verification step (see OtpService). One document per email;
 * sending a new code overwrites it in place rather than creating a second one. */
@Data
@Document(collection = "otps")
public class Otp {

    @Id
    private String id;

    private String email;

    private String code;

    private LocalDateTime sentAt;

    private int attempts;

    private boolean doneVerification;

    // Mongo TTL index on this field — same as the reference's own
    // otpSchema.index({expiresAt:1},{expireAfterSeconds:0}); the document is auto-deleted
    // once expiresAt passes, regardless of whether OtpService ever explicitly deletes it.
    // No @Indexed annotation here: application.properties sets
    // spring.data.mongodb.auto-index-creation=false project-wide (so it'd be inert anyway),
    // and @Indexed's own expireAfterSeconds attribute is deprecated in this Spring Data
    // MongoDB version besides — OtpService's own @PostConstruct creates the index
    // explicitly via MongoTemplate instead, which is the still-supported way to do this.
    private LocalDateTime expiresAt;
}

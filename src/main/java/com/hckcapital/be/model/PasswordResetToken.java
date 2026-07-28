package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Mirrors the old Next.js reference project's own PasswordResetTokenModel (see
 * hckcapital/lib/models/passwordresettoken.ts) field-for-field — same "passwordresettokens"
 * collection (Mongoose's default pluralized name for that model), so a token created here is
 * readable by that app's own reset-password web page and vice versa. Deliberately a
 * standalone collection rather than fields on Member, matching the reference. */
@Data
@Document(collection = "passwordresettokens")
public class PasswordResetToken {

    @Id
    private String id;

    private ObjectId memberId;

    private String token;

    private String email;

    private LocalDateTime expiresAt;
}

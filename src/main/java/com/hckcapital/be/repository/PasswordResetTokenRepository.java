package com.hckcapital.be.repository;

import com.hckcapital.be.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByEmailAndToken(String email, String token);

    void deleteByEmailAndToken(String email, String token);

    void deleteByEmail(String email);
}

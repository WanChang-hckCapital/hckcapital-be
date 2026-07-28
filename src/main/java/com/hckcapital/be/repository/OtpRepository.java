package com.hckcapital.be.repository;

import com.hckcapital.be.model.Otp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpRepository extends MongoRepository<Otp, String> {

    Optional<Otp> findByEmail(String email);

    void deleteByEmail(String email);
}

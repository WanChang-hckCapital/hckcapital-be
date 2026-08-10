package com.hckcapital.be.repository;

import com.hckcapital.be.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProfileRepository extends MongoRepository<Profile, String> {

    // See AuthService.signup's own referral-code redemption — looks up the referrer by the
    // code a new signup optionally typed in.
    Optional<Profile> findByReferralCode(String referralCode);
}

package com.hckcapital.be.repository;

import com.hckcapital.be.model.ReferralHistory;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.Optional;

public interface ReferralHistoryRepository extends MongoRepository<ReferralHistory, String> {

    // See AuthService.signup's own MAX_REFERRAL_IN_MONTH cap — mirrors the old Next.js
    // reference's own createUser (ReferralHistoryModel.countDocuments({referrerId,
    // createdAt: {$gte: startOfMonth}})); GreaterThanEqual (not "After"/$gt) to match that
    // same inclusive boundary.
    long countByReferrerIdAndCreatedAtGreaterThanEqual(ObjectId referrerId, Date startOfMonth);

    // See ProfileService.completeOnboarding's own reward payout — mirrors the reference's
    // own updateMemberDetails (ReferralHistoryModel.findOne({refereeId, status:
    // UNCOMPLETED})). refereeId has a unique index in the shared database (one profile can
    // only ever be referred once), so at most one row can ever match.
    Optional<ReferralHistory> findByRefereeIdAndStatus(ObjectId refereeId, String status);
}

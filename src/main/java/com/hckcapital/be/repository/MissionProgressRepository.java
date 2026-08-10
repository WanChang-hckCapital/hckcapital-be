package com.hckcapital.be.repository;

import com.hckcapital.be.model.MissionProgress;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface MissionProgressRepository extends MongoRepository<MissionProgress, String> {

    List<MissionProgress> findByProfileIdAndPeriodAndPeriodStart(ObjectId profileId, String period, Date periodStart);

    Optional<MissionProgress> findByProfileIdAndPeriodAndPeriodStartAndMissionType(
            ObjectId profileId, String period, Date periodStart, String missionType);

    // See MissionService.syncWeeklyCompletion — counts claimed weekly missions other than
    // the WEEKLY_COMPLETION meta-mission itself, to decide whether the completion bonus
    // should unlock.
    long countByProfileIdAndPeriodAndPeriodStartAndMissionTypeNotAndRewardClaimedTrue(
            ObjectId profileId, String period, Date periodStart, String missionType);
}

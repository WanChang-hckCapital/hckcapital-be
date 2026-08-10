package com.hckcapital.be.repository;

import com.hckcapital.be.model.PointsLog;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PointsLogRepository extends MongoRepository<PointsLog, String> {

    // See ProfileService.getPointsHistory — the reference's own loadPersonalPoints caps at
    // 50 most-recent entries (no true cursor pagination), replicated here via
    // PageRequest.of(0, 50) at the call site.
    List<PointsLog> findByProfileIdOrderByCreatedAtDesc(ObjectId profileId, Pageable pageable);
}

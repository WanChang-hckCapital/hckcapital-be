package com.hckcapital.be.repository;

import com.hckcapital.be.model.SearchHistory;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {

    List<SearchHistory> findByProfileIdOrderBySearchAtDesc(ObjectId profileId, Pageable pageable);

    void deleteByProfileId(ObjectId profileId);
}

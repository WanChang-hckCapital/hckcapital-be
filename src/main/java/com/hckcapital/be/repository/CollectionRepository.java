package com.hckcapital.be.repository;

import com.hckcapital.be.model.Collection;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CollectionRepository extends MongoRepository<Collection, String> {
    List<Collection> findByCreatorOrderByCreatedAtAsc(ObjectId creator);
}

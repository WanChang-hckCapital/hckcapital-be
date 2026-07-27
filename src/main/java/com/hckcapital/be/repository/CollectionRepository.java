package com.hckcapital.be.repository;

import com.hckcapital.be.model.Collection;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionRepository extends MongoRepository<Collection, String> {
    List<Collection> findByCreatorOrderByCreatedAtAsc(ObjectId creator);

    /** Used by both createCollection (reject a duplicate name outright) and
     * updateCollection (reject a duplicate name unless it's this same collection keeping
     * its current name) — see ProfileService for how each interprets the result. */
    Optional<Collection> findByCreatorAndName(ObjectId creator, String name);
}

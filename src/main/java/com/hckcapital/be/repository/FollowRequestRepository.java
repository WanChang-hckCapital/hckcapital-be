package com.hckcapital.be.repository;

import com.hckcapital.be.model.FollowRequest;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FollowRequestRepository extends MongoRepository<FollowRequest, String> {

    List<FollowRequest> findBySender(ObjectId sender);

    boolean existsBySenderAndReceiver(ObjectId sender, ObjectId receiver);
}

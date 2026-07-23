package com.hckcapital.be.repository;

import com.hckcapital.be.model.Subscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {
    List<Subscription> findAllByIdIn(List<String> ids);
}

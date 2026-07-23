package com.hckcapital.be.repository;

import com.hckcapital.be.model.Card;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CardRepository extends MongoRepository<Card, String> {
}

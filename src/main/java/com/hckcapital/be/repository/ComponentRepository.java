package com.hckcapital.be.repository;

import com.hckcapital.be.model.Component;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ComponentRepository extends MongoRepository<Component, String> {
}

package com.hckcapital.be.repository;

import com.hckcapital.be.model.FlxBubbleConstant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FlxBubbleConstantRepository extends MongoRepository<FlxBubbleConstant, String> {
    Optional<FlxBubbleConstant> findByKey(String key);
}

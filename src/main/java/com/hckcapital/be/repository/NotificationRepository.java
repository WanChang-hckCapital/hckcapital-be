package com.hckcapital.be.repository;

import com.hckcapital.be.model.Notification;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByReceiverUserIdOrderByCreatedAtDesc(ObjectId receiverUserId, Pageable pageable);

    long countByReceiverUserIdAndReadFalse(ObjectId receiverUserId);
}

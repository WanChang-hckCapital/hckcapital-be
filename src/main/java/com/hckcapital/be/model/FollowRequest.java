package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "followrequests")
public class FollowRequest {

    @Id
    private String id;

    private ObjectId sender;

    private ObjectId receiver;

    // null = pending, 1 = accepted, 2 = rejected
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

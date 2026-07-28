package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "members")
public class Member {

    @Id
    private String id;

    // Mongoose's own {timestamps: true} equivalent (see MongoAuditingConfig) — populated
    // automatically on insert/update, never set by application code directly.
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ObjectId ref to users collection (NextAuth User)
    private ObjectId user;

    private String generatedId;

    private String email;

    private String password;

    private String phone;

    @Field("ip_address")
    private String ipAddress;

    private String country;

    private String countrycode;

    // array of ObjectId refs to profiles collection
    private List<ObjectId> profiles;

    private int activeProfile;

    private LocalDateTime lastlogin;

    private LocalDateTime deletedAt;
}

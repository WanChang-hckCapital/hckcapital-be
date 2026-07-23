package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Data
@Document(collection = "profiles")
public class Profile {

    @Data
    public static class Follower {
        private ObjectId followersId;
        private Date followedAt;
    }

    @Id
    private String id;

    private String email;

    private String accountname;

    private String imageFilePath;

    private String shortdescription;

    private String usertype;

    private String accountType;

    private String role;

    private Boolean onboarded;

    private Boolean isOnline;

    private String referralCode;

    private Integer bubblePoint;

    private String stripeCustomerId;

    // array of ObjectId refs to subscriptions collection
    private List<ObjectId> subscription;

    // array of ObjectId refs to profiles this profile follows
    private List<ObjectId> following;

    // array of { followersId: ObjectId, followedAt: Date }
    private List<Follower> followers;

    private LocalDateTime deletedAt;
}

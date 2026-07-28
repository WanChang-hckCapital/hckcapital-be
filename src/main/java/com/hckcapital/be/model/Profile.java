package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Mirrors the old Next.js reference project's own Profile model (see hckcapital/lib/
 * models/profile.ts) field-for-field. Fields tied to subsystems never ported to this
 * backend at all (organization/entrepreneur/offers — no Organization/Entrepreneur/Offer
 * model exists here) are kept for schema parity with the shared database but always stay
 * null/empty; nothing in this backend writes to them. */
@Data
@Document(collection = "profiles")
public class Profile {

    @Data
    public static class Follower {
        private ObjectId followersId;
        private Date followedAt;
    }

    @Data
    public static class Preferences {
        private String theme = "light";
        private String language = "en";
        private List<String> categories = new ArrayList<>();
        private Boolean isSkip = false;
        // "home" | "workspace" | "forum" in the reference's own schema enum — not enforced
        // here since nothing in this backend reads/writes tour state yet either.
        private List<String> tour = new ArrayList<>();
    }

    @Data
    public static class Notifications {
        private Boolean email = true;
        private Boolean inApp = true;
    }

    @Data
    public static class ViewDetail {
        private String viewerId;
        private Date viewedAt;
    }

    @Data
    public static class UpdateHistoryEntry {
        private ObjectId profileId;
        private Date timestamp;
    }

    @Id
    private String id;

    // Mongoose's own {timestamps: true} equivalent (see MongoAuditingConfig) — populated
    // automatically on insert/update, never set by application code directly.
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String email;

    private String accountname;

    private String imageFilePath;

    private String shortdescription;

    private String usertype;

    private String accountType;

    private String role;

    private Boolean onboarded;

    // Mongoose defaults every array-type path to [] even without an explicit schema
    // default, and applies each field's own `default:` the moment a document is
    // constructed — matched here the same way, via Java field initializers, so every
    // Profile created anywhere in this backend (signup, Google sign-in, or anything added
    // later) gets these automatically rather than each call site having to remember to set
    // them individually. That per-call-site gap (accountname/usertype/onboarded set,
    // everything else left null) is exactly what was missing before this change.
    private Boolean isOnline = false;

    private Preferences preferences = new Preferences();

    private Notifications notifications = new Notifications();

    // array of ObjectId refs to cards collection
    private List<ObjectId> cards = new ArrayList<>();

    private String referralCode;

    private Integer bubblePoint = 0;

    private String stripeCustomerId;

    // array of ObjectId refs to subscriptions collection
    private List<ObjectId> subscription = new ArrayList<>();

    // array of ObjectId refs to profiles this profile follows
    private List<ObjectId> following = new ArrayList<>();

    // array of { followersId: ObjectId, followedAt: Date }
    private List<Follower> followers = new ArrayList<>();

    // array of ObjectId refs to profiles collection
    private List<ObjectId> closeFriends = new ArrayList<>();

    private List<ObjectId> blockedAccounts = new ArrayList<>();

    private List<ObjectId> mutedAccounts = new ArrayList<>();

    // Always null — no Organization model in this backend, see class-level doc comment.
    private ObjectId organization;

    // Always null — no Entrepreneur model in this backend, see class-level doc comment.
    private ObjectId entrepreneur;

    // Always empty — no Offer model in this backend, see class-level doc comment.
    private List<ObjectId> offers = new ArrayList<>();

    private Integer totalViews = 0;

    private List<ViewDetail> viewDetails = new ArrayList<>();

    private List<UpdateHistoryEntry> updateHistory = new ArrayList<>();

    private LocalDateTime deletedAt;
}

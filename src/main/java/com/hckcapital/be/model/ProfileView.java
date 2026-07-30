package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

// Mirrors the Next.js reference project's lib/models/profileView.ts — same MongoDB
// collection ("profileviews", Mongoose's default pluralized name for model "ProfileView"),
// so both backends read/write the same view history since they share one database.
// Replaces the old design of pushing onto Profile.viewDetails, an unbounded embedded array
// that every read of a Profile document had to carry along with it; see
// ProfileService.recordProfileView.
@Data
@Document(collection = "profileviews")
public class ProfileView {
    @Id
    private String id;

    private ObjectId profileId;

    private ObjectId viewerId;

    private Date viewedAt;
}

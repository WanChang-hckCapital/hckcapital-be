package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Ported from the old Next.js reference's own lib/models/searchhistory.ts — collection
 * name verified via Mongoose's own pluralization (mongoose.model("SearchHistory", ...) →
 * "searchhistories"), same as every other shared collection in this backend. */
@Data
@Document(collection = "searchhistories")
public class SearchHistory {

    @Id
    private String id;

    private ObjectId profileId;

    private String keyword;

    private LocalDateTime searchAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

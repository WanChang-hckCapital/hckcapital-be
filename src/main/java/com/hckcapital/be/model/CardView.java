package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

// Mirrors the Next.js reference project's lib/models/cardView.ts — same MongoDB collection
// ("cardviews", Mongoose's default pluralized name for model "CardView"), so both backends
// read/write the same view history since they share one database. See
// CardService.recordCardView.
@Data
@Document(collection = "cardviews")
public class CardView {
    @Id
    private String id;

    private ObjectId cardId;

    private ObjectId viewerId;

    private Date viewedAt;
}

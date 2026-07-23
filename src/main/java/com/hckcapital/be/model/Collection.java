package com.hckcapital.be.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "collections")
public class Collection {

    @Id
    private String id;

    private String name;

    private ObjectId creator;

    private PublicStatus publicStatus;

    private Boolean isCustom;

    private List<ObjectId> cards;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum PublicStatus {
        PUBLIC,
        PRIVATE
    }
}

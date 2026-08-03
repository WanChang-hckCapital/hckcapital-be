package com.hckcapital.be.service;

import com.hckcapital.be.dto.CardCommentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardCommentService {

    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    /**
     * Returns all comments for a card, sorted by commentDate ascending.
     * isLikedByMe is computed for the given viewer; pass null if unauthenticated.
     */
    public List<CardCommentResponse> fetchCardComments(ObjectId cardId, String viewerProfileId) {

        // 1. Match the card
        MatchOperation matchCard = Aggregation.match(Criteria.where("_id").is(cardId));

        // 2. Unwind card's comments array so each element becomes a separate doc
        //    (preserveNullAndEmptyArrays avoids dropping cards with zero comments)
        UnwindOperation unwindCommentIds = Aggregation.unwind("comments", true);

        // 3. Lookup each comment document from the comments collection
        LookupOperation lookupComment = Aggregation.lookup("comments", "comments", "_id", "commentDoc");

        // 4. Unwind the single-element commentDoc array
        UnwindOperation unwindCommentDoc = Aggregation.unwind("commentDoc", true);

        // 5. Drop rows where commentDoc is null (card has empty comments array)
        MatchOperation filterNullComments = Aggregation.match(Criteria.where("commentDoc").ne(null));

        // 6. Sort by commentDate ascending (oldest first)
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.ASC, "commentDoc.commentDate"));

        // 7. Lookup the commenter's profile
        LookupOperation lookupProfile = Aggregation.lookup("profiles", "commentDoc.commentBy", "_id", "commenter");
        UnwindOperation unwindProfile = Aggregation.unwind("commenter", true);

        // 8. Project the fields we need, including isLikedByMe
        AggregationOperation project;
        if (viewerProfileId != null && ObjectId.isValid(viewerProfileId)) {
            ObjectId viewerId = new ObjectId(viewerProfileId);
            project = ctx -> new Document("$project", new Document()
                    .append("commentID",   "$commentDoc.commentID")
                    .append("comment",     "$commentDoc.comment")
                    .append("commentDate", "$commentDoc.commentDate")
                    .append("likeCount",   new Document("$size",
                            new Document("$ifNull", List.of("$commentDoc.likes", List.of()))))
                    .append("isLikedByMe", new Document("$in",
                            List.of(viewerId, new Document("$ifNull", List.of("$commentDoc.likes", List.of())))))
                    .append("commenter", new Document()
                            .append("_id",           1)
                            .append("accountname",   1)
                            .append("imageFilePath", 1))
            );
        } else {
            project = ctx -> new Document("$project", new Document()
                    .append("commentID",   "$commentDoc.commentID")
                    .append("comment",     "$commentDoc.comment")
                    .append("commentDate", "$commentDoc.commentDate")
                    .append("likeCount",   new Document("$size",
                            new Document("$ifNull", List.of("$commentDoc.likes", List.of()))))
                    .append("isLikedByMe", false)
                    .append("commenter", new Document()
                            .append("_id",           1)
                            .append("accountname",   1)
                            .append("imageFilePath", 1))
            );
        }

        Aggregation aggregation = Aggregation.newAggregation(
                matchCard,
                unwindCommentIds,
                lookupComment,
                unwindCommentDoc,
                filterNullComments,
                sort,
                lookupProfile,
                unwindProfile,
                project
        );

        return mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults()
                .stream()
                .map(this::mapDocument)
                .collect(Collectors.toList());
    }

    /** Creates a comment, links it to the card, and returns it in the same shape as fetchCardComments. */
    public CardCommentResponse addComment(ObjectId cardId, ObjectId profileId, String commentText) {
        ObjectId commentId = new ObjectId();
        Date commentDate = new Date();

        Document commentDoc = new Document("_id", commentId)
                .append("commentID", UUID.randomUUID().toString())
                .append("comment", commentText)
                .append("commentBy", profileId)
                .append("commentDate", commentDate)
                .append("likes", List.of());
        mongoTemplate.insert(commentDoc, "comments");

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(cardId)),
                new Update().push("comments", commentId),
                "cards"
        );

        Document card = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(cardId)),
                Document.class, "cards"
        );
        if (card != null && card.get("creator") instanceof ObjectId creatorId) {
            String snippet = commentText.length() > 80 ? commentText.substring(0, 80) + "…" : commentText;
            notificationService.createNotification(
                    creatorId, profileId, "CARD_COMMENTED", cardId, "card",
                    Map.of("cardTitle", String.valueOf(card.getString("title")), "commentSnippet", snippet)
            );
        }

        Document commenter = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(profileId)),
                Document.class, "profiles"
        );
        String commenterName = commenter != null ? commenter.getString("accountname") : null;
        String commenterImg = commenter != null ? commenter.getString("imageFilePath") : null;

        return new CardCommentResponse(
                commentDoc.getString("commentID"), commentText,
                profileId.toHexString(), commenterName, commenterImg,
                0, false,
                commentDate
        );
    }

    private CardCommentResponse mapDocument(Document doc) {
        String commentID = doc.getString("commentID");
        String comment   = doc.getString("comment");
        Date commentDate = doc.getDate("commentDate");
        int likeCount    = doc.getInteger("likeCount", 0);
        boolean liked    = doc.getBoolean("isLikedByMe", false);

        Document commenter = doc.get("commenter", Document.class);
        String commenterId   = null;
        String commenterName = null;
        String commenterImg  = null;
        if (commenter != null) {
            Object id = commenter.get("_id");
            commenterId   = id != null ? id.toString() : null;
            commenterName = commenter.getString("accountname");
            commenterImg  = commenter.getString("imageFilePath");
        }

        return new CardCommentResponse(
                commentID, comment,
                commenterId, commenterName, commenterImg,
                likeCount, liked,
                commentDate
        );
    }
}

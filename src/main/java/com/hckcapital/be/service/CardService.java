package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hckcapital.be.dto.CardCategoryCountResponse;
import com.hckcapital.be.dto.CardLikeToggleResponse;
import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.SaveCardRequest;
import com.hckcapital.be.dto.SaveCardResponse;
import com.hckcapital.be.model.Card;
import com.hckcapital.be.model.Component;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.repository.CardRepository;
import com.hckcapital.be.repository.ComponentRepository;
import com.hckcapital.be.repository.MemberRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final MongoTemplate mongoTemplate;
    private final CardRepository cardRepository;
    private final ComponentRepository componentRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public CardPageResponse fetchAllCards(int page, int limit, String profileId) {
        int skip = (page - 1) * limit;

        // 1. Filter published, non-deleted cards
        MatchOperation matchPublished = Aggregation.match(
                Criteria.where("isReadyToPublish").is(true)
                        .and("deleteInfo.isDeleted").ne(true)
        );

        // 2. Sort by createdAt desc, _id desc
        SortOperation sort = Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id"))
        );

        // 3. Join creator profile
        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");

        // 4. Unwind creator
        UnwindOperation unwindCreator = Aggregation.unwind("creator");

        // 5. Access control: PUBLIC profiles or own cards
        MatchOperation matchAccess;
        if (profileId != null && !profileId.isEmpty()) {
            matchAccess = Aggregation.match(new Criteria().orOperator(
                    Criteria.where("creator.accountType").is("PUBLIC"),
                    Criteria.where("creator._id").is(new ObjectId(profileId))
            ));
        } else {
            matchAccess = Aggregation.match(Criteria.where("creator.accountType").is("PUBLIC"));
        }

        // 6. Paginate BEFORE heavy joins
        SkipOperation skipOp = Aggregation.skip((long) skip);
        LimitOperation limitOp = Aggregation.limit(limit);

        // 7. Join line format component
        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);

        // 8. Join flex html component
        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);

        // 9. Project only needed fields — include isLikedByMe when viewer is known
        final boolean hasViewer = profileId != null && !profileId.isEmpty() && ObjectId.isValid(profileId);
        AggregationOperation project = ctx -> {
            Document proj = new Document()
                    .append("title", 1).append("description", 1)
                    .append("shareCount", 1)
                    .append("comments", 1)
                    .append("likes", 1)
                    .append("cardShareTitle", 1)
                    .append("creator", new Document()
                            .append("_id", 1)
                            .append("accountname", 1)
                            .append("imageFilePath", 1)
                    )
                    .append("lineComponentData", new Document().append("content", 1))
                    .append("flexHtmlData", new Document().append("content", 1));
            if (hasViewer) {
                proj.append("isLikedByMe", new Document("$in", List.of(
                        new ObjectId(profileId),
                        new Document("$ifNull", List.of("$likes", List.of()))
                )));
            } else {
                // A plain boolean false here (rather than wrapped in $literal) is
                // indistinguishable from an exclusion flag (0/false) to MongoDB's $project
                // stage — since every other field in this projection is an inclusion (1),
                // that throws "Cannot do exclusion on field isLikedByMe in inclusion
                // projection" (error 31254) the moment this branch is actually taken (i.e.
                // whenever there's no viewer). $literal forces it to be treated as the
                // constant value it's meant to be.
                proj.append("isLikedByMe", new Document("$literal", false));
            }
            return new Document("$project", proj);
        };

        Aggregation aggregation = Aggregation.newAggregation(
                matchPublished,
                sort,
                lookupCreator,
                unwindCreator,
                matchAccess,
                skipOp,
                limitOp,
                lookupLine,
                unwindLine,
                lookupFlex,
                unwindFlex,
                project
        );

        List<Document> results = mongoTemplate
                .aggregate(aggregation, "cards", Document.class)
                .getMappedResults();

//        log.info("fetchAllCards page={} profileId={} → {} results", page, profileId, results.size());

        List<CardSummaryResponse> cards = results.stream()
                .map(this::mapDocument)
                .collect(java.util.stream.Collectors.toList());

        return new CardPageResponse(cards, cards.size() == limit);
    }

    /** Paginated published or draft cards for a single creator. */
    public CardPageResponse fetchCardsByCreator(ObjectId creatorId, boolean published, int page, int limit, String viewerProfileId) {
        int skip = (page - 1) * limit;

        Criteria criteria = Criteria.where("deleteInfo.isDeleted").ne(true);
        if (published) {
            criteria.and("creator").is(creatorId).and("isReadyToPublish").is(true);
        } else {
            criteria.and("creator").is(creatorId).and("isReadyToPublish").ne(true);
        }

        MatchOperation matchCards = Aggregation.match(criteria);
        SortOperation sort = Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id"))
        );
        SkipOperation skipOp = Aggregation.skip((long) skip);
        LimitOperation limitOp = Aggregation.limit(limit);
        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");
        UnwindOperation unwindCreator = Aggregation.unwind("creator");
        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);
        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);

        final boolean hasViewer = viewerProfileId != null && ObjectId.isValid(viewerProfileId);
        AggregationOperation project = ctx -> {
            Document proj = new Document()
                    .append("title", 1).append("shareCount", 1).append("comments", 1)
                    .append("likes", 1).append("cardShareTitle", 1)
                    .append("creator", new Document().append("_id", 1).append("accountname", 1).append("imageFilePath", 1))
                    .append("lineComponentData", new Document().append("content", 1))
                    .append("flexHtmlData", new Document().append("content", 1));
            if (hasViewer) {
                proj.append("isLikedByMe", new Document("$in", List.of(
                        new ObjectId(viewerProfileId),
                        new Document("$ifNull", List.of("$likes", List.of()))
                )));
            } else {
                // A plain boolean false here (rather than wrapped in $literal) is
                // indistinguishable from an exclusion flag (0/false) to MongoDB's $project
                // stage — since every other field in this projection is an inclusion (1),
                // that throws "Cannot do exclusion on field isLikedByMe in inclusion
                // projection" (error 31254) the moment this branch is actually taken (i.e.
                // whenever there's no viewer). $literal forces it to be treated as the
                // constant value it's meant to be.
                proj.append("isLikedByMe", new Document("$literal", false));
            }
            return new Document("$project", proj);
        };

        Aggregation aggregation = Aggregation.newAggregation(
                matchCards, sort, skipOp, limitOp,
                lookupCreator, unwindCreator,
                lookupLine, unwindLine, lookupFlex, unwindFlex, project
        );
        List<CardSummaryResponse> cards = mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults().stream()
                .map(this::mapDocument)
                .collect(java.util.stream.Collectors.toList());
        return new CardPageResponse(cards, cards.size() == limit);
    }

    /**
     * Paginated published cards tagged with a template category — the RN editor's "Choose a
     * Template" sheet, mirroring the Next.js reference project's fetchCardsByCategory
     * (lib/actions/user.actions.ts). Unlike fetchAllCards/fetchCardsByCreator, only
     * FLEXADMIN-authored, publicly-visible cards qualify as templates, matching the
     * reference's own creator.usertype/accountType filter — an ordinary user's public card
     * that happens to share a category label is never offered as a template. Also joins
     * `components` (unlike those two) since applying a template needs the raw editor tree,
     * not just the rendered html/lineComponents — same lookup fetchCardById already does for
     * the single-card "reopen for editing" case.
     */
    public CardPageResponse fetchCardsByCategory(String category, int page, int limit) {
        int skip = (page - 1) * limit;

        MatchOperation matchCards = Aggregation.match(
                Criteria.where("categories").is(category)
                        .and("status").regex("^public$", "i")
                        .and("deleteInfo.isDeleted").ne(true)
        );
        SortOperation sort = Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id"))
        );
        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");
        UnwindOperation unwindCreator = Aggregation.unwind("creator");
        MatchOperation matchCreator = Aggregation.match(
                Criteria.where("creator.usertype").is("FLEXADMIN")
                        .and("creator.accountType").is("PUBLIC")
        );
        SkipOperation skipOp = Aggregation.skip((long) skip);
        LimitOperation limitOp = Aggregation.limit(limit);
        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);
        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);
        LookupOperation lookupComponent = Aggregation.lookup("components", "components", "_id", "componentData");
        UnwindOperation unwindComponent = Aggregation.unwind("componentData", true);

        // No isLikedByMe here (unlike fetchAllCards/fetchCardsByCreator/fetchCardsByIds/
        // fetchCardById) — this method takes no viewerProfileId at all, so there's no
        // meaningful value to compute; mapDocument's own getBoolean(..., false) default
        // already covers its absence from the projection. A literal `false` mixed into an
        // otherwise-inclusion ($project field: 1) projection would also be invalid MongoDB
        // (see the $literal fix on the other four methods' own isLikedByMe branch) — simplest
        // to just not project a field nothing here can compute anyway.
        AggregationOperation project = ctx -> new Document("$project", new Document()
                .append("title", 1).append("shareCount", 1).append("comments", 1)
                .append("likes", 1).append("cardShareTitle", 1)
                .append("creator", new Document().append("_id", 1).append("accountname", 1).append("imageFilePath", 1))
                .append("lineComponentData", new Document().append("content", 1))
                .append("flexHtmlData", new Document().append("content", 1))
                .append("componentData", new Document().append("content", 1)));

        Aggregation aggregation = Aggregation.newAggregation(
                matchCards, sort,
                lookupCreator, unwindCreator, matchCreator,
                skipOp, limitOp,
                lookupLine, unwindLine, lookupFlex, unwindFlex,
                lookupComponent, unwindComponent,
                project
        );
        List<CardSummaryResponse> cards = mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults().stream()
                .map(this::mapDocument)
                .collect(java.util.stream.Collectors.toList());
        return new CardPageResponse(cards, cards.size() == limit);
    }

    /**
     * Per-category counts across every template category a FLEXADMIN has published to —
     * the RN "Choose a Template" sheet's sidebar badges, mirroring the Next.js reference
     * project's fetchCategoryCounts (lib/actions/user.actions.ts). Same access filter as
     * fetchCardsByCategory (public, not deleted, FLEXADMIN + PUBLIC creator), just grouped
     * by `categories` instead of paginated within one — a card with N categories is counted
     * once per category, same as the reference's own $unwind-then-$group.
     */
    public List<CardCategoryCountResponse> fetchCategoryCounts() {
        MatchOperation matchPublished = Aggregation.match(
                Criteria.where("status").regex("^public$", "i")
                        .and("deleteInfo.isDeleted").ne(true)
        );
        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creatorProfile");
        UnwindOperation unwindCreator = Aggregation.unwind("creatorProfile");
        MatchOperation matchCreator = Aggregation.match(
                Criteria.where("creatorProfile.usertype").is("FLEXADMIN")
                        .and("creatorProfile.accountType").is("PUBLIC")
        );
        UnwindOperation unwindCategories = Aggregation.unwind("categories");
        GroupOperation group = Aggregation.group("categories").count().as("count");
        AggregationOperation project = ctx -> new Document("$project", new Document()
                .append("category", "$_id").append("count", 1).append("_id", 0));
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.ASC, "category"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchPublished, lookupCreator, unwindCreator, matchCreator,
                unwindCategories, group, project, sort
        );

        return mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults().stream()
                .map(doc -> new CardCategoryCountResponse(doc.getString("category"), doc.getInteger("count", 0)))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<CardSummaryResponse> fetchCardsByIds(List<ObjectId> cardIds, String viewerProfileId) {
        if (cardIds == null || cardIds.isEmpty()) return List.of();

        MatchOperation matchCards = Aggregation.match(
                Criteria.where("_id").in(cardIds)
                        .and("isReadyToPublish").is(true)
                        .and("deleteInfo.isDeleted").ne(true)
        );

        SortOperation sort = Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id"))
        );

        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");
        UnwindOperation unwindCreator = Aggregation.unwind("creator");

        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);

        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);

        final boolean hasViewer = viewerProfileId != null && ObjectId.isValid(viewerProfileId);
        AggregationOperation project = ctx -> {
            Document proj = new Document()
                    .append("title", 1)
                    .append("shareCount", 1)
                    .append("comments", 1)
                    .append("likes", 1)
                    .append("cardShareTitle", 1)
                    .append("creator", new Document()
                            .append("_id", 1)
                            .append("accountname", 1)
                            .append("imageFilePath", 1))
                    .append("lineComponentData", new Document().append("content", 1))
                    .append("flexHtmlData", new Document().append("content", 1));
            if (hasViewer) {
                proj.append("isLikedByMe", new Document("$in", List.of(
                        new ObjectId(viewerProfileId),
                        new Document("$ifNull", List.of("$likes", List.of()))
                )));
            } else {
                // A plain boolean false here (rather than wrapped in $literal) is
                // indistinguishable from an exclusion flag (0/false) to MongoDB's $project
                // stage — since every other field in this projection is an inclusion (1),
                // that throws "Cannot do exclusion on field isLikedByMe in inclusion
                // projection" (error 31254) the moment this branch is actually taken (i.e.
                // whenever there's no viewer). $literal forces it to be treated as the
                // constant value it's meant to be.
                proj.append("isLikedByMe", new Document("$literal", false));
            }
            return new Document("$project", proj);
        };

        Aggregation aggregation = Aggregation.newAggregation(
                matchCards, sort, lookupCreator, unwindCreator,
                lookupLine, unwindLine, lookupFlex, unwindFlex, project
        );

        return mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults().stream()
                .map(this::mapDocument)
                .collect(java.util.stream.Collectors.toList());
    }

    public CardSummaryResponse fetchCardById(String cardId, String viewerProfileId) {
        MatchOperation matchCard = Aggregation.match(
                Criteria.where("_id").is(new ObjectId(cardId))
                        .and("deleteInfo.isDeleted").ne(true)
        );
        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");
        UnwindOperation unwindCreator = Aggregation.unwind("creator");
        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);
        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);
        // Only looked up here, not in the list endpoints (fetchAllCards/fetchCardsByCreator/
        // fetchCardsByIds) — this is the raw editor tree, needed only when reopening a
        // single card for editing (see EditCardScreen), not worth the extra join/payload on
        // every feed page.
        LookupOperation lookupComponent = Aggregation.lookup("components", "components", "_id", "componentData");
        UnwindOperation unwindComponent = Aggregation.unwind("componentData", true);

        final boolean hasViewer = viewerProfileId != null && ObjectId.isValid(viewerProfileId);
        AggregationOperation project = ctx -> {
            Document proj = new Document()
                    .append("title", 1).append("description", 1)
                    .append("shareCount", 1).append("comments", 1).append("likes", 1).append("cardShareTitle", 1)
                    .append("creator", new Document().append("_id", 1).append("accountname", 1).append("imageFilePath", 1))
                    .append("lineComponentData", new Document().append("content", 1))
                    .append("flexHtmlData", new Document().append("content", 1))
                    .append("componentData", new Document().append("content", 1));
            if (hasViewer) {
                proj.append("isLikedByMe", new Document("$in", List.of(
                        new ObjectId(viewerProfileId),
                        new Document("$ifNull", List.of("$likes", List.of()))
                )));
            } else {
                // A plain boolean false here (rather than wrapped in $literal) is
                // indistinguishable from an exclusion flag (0/false) to MongoDB's $project
                // stage — since every other field in this projection is an inclusion (1),
                // that throws "Cannot do exclusion on field isLikedByMe in inclusion
                // projection" (error 31254) the moment this branch is actually taken (i.e.
                // whenever there's no viewer). $literal forces it to be treated as the
                // constant value it's meant to be.
                proj.append("isLikedByMe", new Document("$literal", false));
            }
            return new Document("$project", proj);
        };

        Aggregation aggregation = Aggregation.newAggregation(
                matchCard, lookupCreator, unwindCreator,
                lookupLine, unwindLine, lookupFlex, unwindFlex,
                lookupComponent, unwindComponent, project
        );
        return mongoTemplate.aggregate(aggregation, "cards", Document.class)
                .getMappedResults().stream().findFirst().map(this::mapDocument).orElse(null);
    }

    private CardSummaryResponse mapDocument(Document doc) {
        CardSummaryResponse card = new CardSummaryResponse();
        card.setCardId(doc.getObjectId("_id").toHexString());
        card.setTitle(doc.getString("title"));
        card.setDescription(doc.getString("description"));
        card.setShareCount(doc.getInteger("shareCount", 0));

        Object likesObj = doc.get("likes");
        card.setLikes(likesObj instanceof List ? ((List<?>) likesObj).size() : 0);

        Object commentsObj = doc.get("comments");
        card.setComments(commentsObj instanceof List ? ((List<?>) commentsObj).size() : 0);

        Object shareTitles = doc.get("cardShareTitle");
        if (shareTitles instanceof List<?> list && !list.isEmpty()) {
            card.setCardShareTitle(list.get(0).toString());
            card.setCardShareTitles(list.stream().map(Object::toString).collect(java.util.stream.Collectors.toList()));
        }

        Document componentData = doc.get("componentData", Document.class);
        if (componentData != null) {
            card.setEditorJson(componentData.getString("content"));
        }

        Document creator = doc.get("creator", Document.class);
        if (creator != null) {
            Object creatorId = creator.get("_id");
            card.setCreatorId(creatorId != null ? creatorId.toString() : null);
            card.setCreatorAccountName(creator.getString("accountname"));
            card.setCreatorImage(creator.getString("imageFilePath"));
        }

        Document flexHtmlData = doc.get("flexHtmlData", Document.class);
        if (flexHtmlData != null) {
            card.setHtml(flexHtmlData.getString("content"));
        }

        Document lineComponentData = doc.get("lineComponentData", Document.class);
        if (lineComponentData != null) {
            card.setLineComponentsJson(lineComponentData.getString("content"));
        }

        card.setLikedByMe(doc.getBoolean("isLikedByMe", false));

        return card;
    }

    public List<FollowUserResponse> fetchCardLiker(ObjectId cardId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").is(cardId)),
                Aggregation.lookup("profiles", "likes", "_id", "likerProfiles"),
                Aggregation.unwind("likerProfiles"),
                ctx -> new Document("$project", new Document()
                        .append("_id", "$likerProfiles._id")
                        .append("accountname", "$likerProfiles.accountname")
                        .append("imageFilePath", "$likerProfiles.imageFilePath"))
        );
        return mongoTemplate.aggregate(agg, "cards", Document.class)
                .getMappedResults()
                .stream()
                .map(doc -> {
                    Object id = doc.get("_id");
                    return new FollowUserResponse(
                            id != null ? id.toString() : null,
                            doc.getString("accountname"),
                            doc.getString("imageFilePath")
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public CardLikeToggleResponse toggleLike(ObjectId cardId, ObjectId profileId) {
        Query query = Query.query(Criteria.where("_id").is(cardId));
        boolean alreadyLiked = mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(cardId).and("likes").is(profileId)),
                "cards"
        );

        Update update = alreadyLiked
                ? new Update().pull("likes", profileId)
                : new Update().addToSet("likes", profileId);
        mongoTemplate.updateFirst(query, update, "cards");

        Document result = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(cardId)),
                Document.class, "cards"
        );
        Object likesObj = result != null ? result.get("likes") : null;
        int likeCount = likesObj instanceof List ? ((List<?>) likesObj).size() : 0;

        return new CardLikeToggleResponse(!alreadyLiked, likeCount);
    }

    /**
     * Creates or updates a card in one call, mirroring the Next.js reference's
     * upsertCardContent — presence of request.cardId decides create vs. update. Unlike the
     * reference (which trusts a caller-supplied profileId), the creator/owner is always
     * derived from the authenticated member's active profile, and an update is rejected
     * unless the existing card's creator matches — the reference never checked this at the
     * mutation itself, only at page-render time.
     */
    public SaveCardResponse saveCard(String memberId, SaveCardRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        List<ObjectId> profiles = member.getProfiles();
        if (profiles == null || profiles.isEmpty() || member.getActiveProfile() >= profiles.size()) {
            throw new RuntimeException("No active profile for member");
        }
        ObjectId activeProfileId = profiles.get(member.getActiveProfile());

        boolean isNewCard = request.getCardId() == null || request.getCardId().isBlank();
        Card card;

        if (isNewCard) {
            card = new Card();
            card.setCardID(UUID.randomUUID().toString());
            card.setCreator(activeProfileId);
            card.setComponents(saveComponent("flexCard", request.getEditorJson()));
            card.setLineFormatComponent(saveComponent("line", request.getLineComponentsJson()));
            card.setFlexFormatHtml(saveComponent("html", request.getHtml()));
            card.setLikes(new ArrayList<>());
            card.setLikeMissionCreditedBy(new ArrayList<>());
            card.setFollowers(new ArrayList<>());
            card.setComments(new ArrayList<>());
            card.setTotalViews(0);
            card.setShareCount(0);
            Card.DeleteInfo deleteInfo = new Card.DeleteInfo();
            deleteInfo.setIsDeleted(false);
            card.setDeleteInfo(deleteInfo);
            card.setCreatedAt(LocalDateTime.now());
        } else {
            if (!ObjectId.isValid(request.getCardId())) {
                throw new RuntimeException("Invalid cardId");
            }
            card = cardRepository.findById(request.getCardId())
                    .orElseThrow(() -> new RuntimeException("Card not found"));
            if (!activeProfileId.equals(card.getCreator())) {
                throw new SecurityException("You do not own this card");
            }
            updateComponent(card.getComponents(), request.getEditorJson());
            updateComponent(card.getLineFormatComponent(), request.getLineComponentsJson());
            updateComponent(card.getFlexFormatHtml(), request.getHtml());
        }

        card.setTitle(request.getTitle());
        card.setDescription(request.getDescription());
        card.setStatus("PUBLIC");
        card.setCategories(request.getCategories());
        card.setCardShareTitle(request.getCardShareTitle());
        card.setIsReadyToPublish(true);
        card.setIsContainingVideo(hasValidVideoOrImageHero(request.getLineComponentsJson()));
        card.setUpdatedAt(LocalDateTime.now());

        Card saved = cardRepository.save(card);

        if (isNewCard) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(activeProfileId)),
                    new Update().push("cards", new ObjectId(saved.getId())),
                    "profiles"
            );
        }

        return new SaveCardResponse(saved.getId());
    }

    private ObjectId saveComponent(String componentType, String content) {
        Component component = new Component();
        component.setComponentID(UUID.randomUUID().toString());
        component.setComponentType(componentType);
        component.setContent(content);
        Component saved = componentRepository.save(component);
        return new ObjectId(saved.getId());
    }

    private void updateComponent(ObjectId componentId, String content) {
        if (componentId == null) return;
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(componentId)),
                new Update().set("content", content),
                "components"
        );
    }

    /** Marks a card as containing a playable hero (video, or image hero with a url) — same
     * check the reference project uses to decide whether a card is eligible for the
     * video-feed/"reel" feature. Malformed JSON is treated as no video, not an error. */
    private boolean hasValidVideoOrImageHero(String lineComponentsJson) {
        try {
            JsonNode root = objectMapper.readTree(lineComponentsJson);
            return hasValidVideoOrImageHero(root);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasValidVideoOrImageHero(JsonNode node) {
        if (node == null || !node.isObject()) return false;

        String type = node.path("type").asText(null);
        if ("bubble".equals(type)) {
            JsonNode hero = node.path("hero");
            String heroType = hero.path("type").asText(null);
            String url = hero.path("url").asText(null);
            return ("video".equals(heroType) || "image".equals(heroType)) && url != null && !url.isBlank();
        }
        if ("carousel".equals(type)) {
            for (JsonNode bubble : node.path("contents")) {
                if (hasValidVideoOrImageHero(bubble)) return true;
            }
        }
        return false;
    }
}

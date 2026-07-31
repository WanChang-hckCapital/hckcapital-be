package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hckcapital.be.dto.CardCategoryCountResponse;
import com.hckcapital.be.dto.CardLikeToggleResponse;
import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CardViewCountryResponse;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.SaveCardRequest;
import com.hckcapital.be.dto.SaveCardResponse;
import com.hckcapital.be.model.Card;
import com.hckcapital.be.model.CardView;
import com.hckcapital.be.model.Component;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.repository.CardRepository;
import com.hckcapital.be.repository.ComponentRepository;
import com.hckcapital.be.repository.MemberRepository;
import com.hckcapital.be.repository.ProfileRepository;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final MongoTemplate mongoTemplate;
    private final CardRepository cardRepository;
    private final ComponentRepository componentRepository;
    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
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

    /** Paginated published or draft cards for a single creator, with no date filtering —
     * see the overload below for Settings > Report's own "Content" tab / period dropdown. */
    public CardPageResponse fetchCardsByCreator(ObjectId creatorId, boolean published, int page, int limit, String viewerProfileId) {
        return fetchCardsByCreator(creatorId, published, page, limit, viewerProfileId, null, null);
    }

    /** Same as the no-date overload above, plus an optional `createdAt` range — either
     * bound may be null to leave that side open. Used by Settings > Report's own period
     * dropdown (see ProfileController.getPublishedCards's own startDate/endDate params) to
     * scope the "Content" tab's card list to a selected window; every other caller just
     * uses the no-date overload, which passes null/null through here unfiltered. */
    public CardPageResponse fetchCardsByCreator(
            ObjectId creatorId, boolean published, int page, int limit, String viewerProfileId,
            LocalDateTime startDate, LocalDateTime endDate
    ) {
        int skip = (page - 1) * limit;

        Criteria criteria = Criteria.where("deleteInfo.isDeleted").ne(true);
        if (published) {
            criteria.and("creator").is(creatorId).and("isReadyToPublish").is(true);
        } else {
            criteria.and("creator").is(creatorId).and("isReadyToPublish").ne(true);
        }
        // A single `.and("createdAt")` call, not two — Spring Data's Criteria throws
        // InvalidMongoDbApiUsageException ("can't add a second '$and' expression for the
        // same field") if the same field is `.and(...)`'d more than once on one Criteria.
        if (startDate != null && endDate != null) {
            criteria.and("createdAt").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            criteria.and("createdAt").gte(startDate);
        } else if (endDate != null) {
            criteria.and("createdAt").lte(endDate);
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
                    .append("likes", 1).append("cardShareTitle", 1).append("totalViews", 1).append("createdAt", 1)
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
        populateSaveCounts(cards);
        return new CardPageResponse(cards, cards.size() == limit);
    }

    /** "Saves" (PostInsightsScreen's own 儲存 stat) means "does this card belong to one of
     * my Collections" — i.e. how many distinct profiles have this card in at least one of
     * their own Collection.cards arrays (see Collection.java; NAMECARD/CONVERSATION or any
     * custom collection all count). One batched query for every card on the page rather
     * than one query per card — same reasoning as the rest of this method's own
     * likes/comments handling: a personal card list is small enough that a single lookup
     * plus in-memory grouping is simpler than an aggregation-pipeline $lookup/$group. A
     * profile that saved the same card into two of its own collections still only counts
     * once, same as a "like" can't be double-counted from one profile. */
    private void populateSaveCounts(List<CardSummaryResponse> cards) {
        if (cards.isEmpty()) return;
        List<ObjectId> cardIds = cards.stream().map(c -> new ObjectId(c.getCardId())).collect(Collectors.toList());
        List<Document> collections = mongoTemplate.find(
                Query.query(Criteria.where("cards").in(cardIds)),
                Document.class, "collections"
        );

        Map<ObjectId, Set<ObjectId>> saversByCardId = new HashMap<>();
        for (Document collection : collections) {
            Object creatorObj = collection.get("creator");
            if (!(creatorObj instanceof ObjectId creator)) continue;
            Object cardsObj = collection.get("cards");
            if (!(cardsObj instanceof List<?> collectionCardIds)) continue;
            for (Object cardIdObj : collectionCardIds) {
                if (cardIdObj instanceof ObjectId cardId) {
                    saversByCardId.computeIfAbsent(cardId, k -> new HashSet<>()).add(creator);
                }
            }
        }

        for (CardSummaryResponse card : cards) {
            Set<ObjectId> savers = saversByCardId.get(new ObjectId(card.getCardId()));
            card.setSaves(savers != null ? savers.size() : 0);
        }
    }

    /**
     * Paginated soft-deleted cards for a single creator — the Recycle Bin screen, mirroring
     * the Next.js reference project's fetchRecycleBinBubble (lib/actions/user.actions.ts).
     * Same shape as fetchCardsByCreator just above, with two differences: matches
     * deleteInfo.isDeleted true instead of ne(true) (the whole point of this query), and
     * doesn't filter on isReadyToPublish at all — a card can land in the bin whether it was
     * published or still a draft when deleted, so both should show up here. Unlike the
     * reference (which scopes via `_id: {$in: profile.cards}`, a separate array kept on the
     * Profile document), this matches directly on `creator`, consistent with every other
     * per-creator query in this file (fetchCardsByCreator/countPublishedCards/
     * countDraftCards) — this port never introduced that parallel Profile.cards array, so
     * `creator` is the one authoritative source of "whose card is this" throughout.
     */
    public CardPageResponse fetchDeletedCardsByCreator(ObjectId creatorId, int page, int limit, String viewerProfileId) {
        int skip = (page - 1) * limit;

        Criteria criteria = Criteria.where("creator").is(creatorId)
                .and("deleteInfo.isDeleted").is(true);

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
                    .append("isReadyToPublish", 1)
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

        card.setViews(doc.getInteger("totalViews", 0));
        card.setCreatedAt(doc.getDate("createdAt"));
        // `saves` isn't set here — it needs a cross-collection lookup (which profiles have
        // this card in one of their own Collections), not anything on the Card document
        // itself. See fetchCardsByCreator's own post-processing step, populateSaveCounts.

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
        card.setReadyToPublish(doc.getBoolean("isReadyToPublish", false));

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

    /** Records a card view in the CardView collection — see CardView's own doc comment for
     * why this isn't stored on Card.viewDetails (an unbounded embedded array, and one that
     * was never even kept in sync with Card.totalViews). Skips self-views (the creator
     * viewing their own card) and dedups repeat views from the same viewer within a
     * 3-minute window, mirroring recordProfileView / the Next.js reference's own
     * updateCardViewDate. */
    public void recordCardView(String cardId, String viewerProfileId) {
        if (cardId == null || viewerProfileId == null || !ObjectId.isValid(cardId)) {
            return;
        }

        Document card = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(new ObjectId(cardId))),
                Document.class, "cards"
        );
        if (card == null) {
            return;
        }
        Object creator = card.get("creator");
        if (creator != null && creator.toString().equals(viewerProfileId)) {
            return;
        }

        ObjectId cardObjectId = new ObjectId(cardId);
        ObjectId viewerObjectId = new ObjectId(viewerProfileId);
        Date now = new Date();
        Date threeMinutesAgo = new Date(now.getTime() - 3 * 60 * 1000);

        Criteria recentCriteria = Criteria.where("cardId").is(cardObjectId)
                .and("viewerId").is(viewerObjectId)
                .and("viewedAt").gt(threeMinutesAgo);
        boolean recentView = mongoTemplate.exists(Query.query(recentCriteria), CardView.class);
        if (recentView) {
            return;
        }

        CardView view = new CardView();
        view.setCardId(cardObjectId);
        view.setViewerId(viewerObjectId);
        view.setViewedAt(now);
        mongoTemplate.insert(view);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(cardObjectId)),
                new Update().inc("totalViews", 1),
                Card.class
        );
    }

    /** Powers PostInsightsScreen's "國家地區" (country/region) breakdown — what share of a
     * card's distinct viewers come from each country, sorted by count descending.
     *
     * Profile itself doesn't store a country — that lives on Member (see
     * AccountDetailsResponse). So this reverse-looks-up each distinct viewer's owning
     * Member via `profiles $in viewerIds` (a Member's `profiles` array contains every
     * profile it owns) and reads that Member's own `country`. Viewers whose Member has no
     * country set land in an "Unknown" bucket rather than being dropped from the total —
     * same honesty principle as everywhere else in this Report screen: no fabricated data,
     * but also no silently-incomplete percentages. */
    public List<CardViewCountryResponse> getCardViewCountryBreakdown(String cardId) {
        if (!ObjectId.isValid(cardId)) {
            return List.of();
        }
        List<ObjectId> viewerIds = mongoTemplate.findDistinct(
                Query.query(Criteria.where("cardId").is(new ObjectId(cardId))),
                "viewerId", "cardviews", ObjectId.class
        );
        if (viewerIds.isEmpty()) {
            return List.of();
        }

        List<Member> members = mongoTemplate.find(
                Query.query(Criteria.where("profiles").in(viewerIds)),
                Member.class
        );

        Map<ObjectId, String> countryByViewerId = new HashMap<>();
        for (Member member : members) {
            if (member.getProfiles() == null) continue;
            for (ObjectId profileId : member.getProfiles()) {
                if (viewerIds.contains(profileId)) {
                    countryByViewerId.put(profileId, member.getCountry());
                }
            }
        }

        Map<String, Integer> countsByCountry = new HashMap<>();
        for (ObjectId viewerId : viewerIds) {
            String country = countryByViewerId.get(viewerId);
            String key = (country == null || country.isBlank()) ? "Unknown" : country;
            countsByCountry.merge(key, 1, Integer::sum);
        }

        int total = viewerIds.size();
        return countsByCountry.entrySet().stream()
                .map(e -> new CardViewCountryResponse(e.getKey(), e.getValue(), Math.round((e.getValue() * 1000.0) / total) / 10.0))
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
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
        card.setIsReadyToPublish(request.getIsReadyToPublish() == null || request.getIsReadyToPublish());
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

    /**
     * Soft-delete or restore a card — moves it into (or out of) the Recycle Bin. Mirrors the
     * Next.js reference project's own updateDeleteCardStatus, unified into one method with a
     * boolean rather than a string "action" parameter (matching e.g. the RN editor's own
     * ADD_BUBBLE/MOVE_ELEMENT precedent of "one command, a field picks the variant" instead
     * of splitting into two near-identical methods). `deletedDate` is set on delete and
     * cleared entirely on restore (not just nulled) — same as the reference's own $unset —
     * since a future scheduled purge job would key off "does this field exist" the same way
     * the reference's own MongoDB TTL index does.
     *
     * No-ops (via the query's own deleteInfo.isDeleted match) if the card is already in the
     * requested state — restoring a card that isn't deleted, or deleting one that already
     * is, doesn't throw, it just doesn't match any document to update. Authorization: only
     * the card's own creator or a FLEXADMIN may do either — see authorizeCardMutation.
     */
    public void setCardDeleted(String memberId, String cardId, boolean deleted) {
        if (!ObjectId.isValid(cardId)) {
            throw new RuntimeException("Invalid cardId");
        }
        ObjectId requesterProfileId = resolveActiveProfileId(memberId);
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        authorizeCardMutation(card, requesterProfileId);

        Query query = Query.query(
                Criteria.where("_id").is(new ObjectId(cardId))
                        .and("deleteInfo.isDeleted").is(!deleted)
        );
        Update update = deleted
                ? new Update().set("deleteInfo.isDeleted", true).set("deleteInfo.deletedDate", new Date())
                : new Update().set("deleteInfo.isDeleted", false).unset("deleteInfo.deletedDate");
        mongoTemplate.updateFirst(query, update, "cards");
    }

    /** Flips a draft card (isReadyToPublish: false — e.g. one created via the Shortcut
     * feature) to published. Same creator-or-FLEXADMIN authorization every other card
     * mutation here uses; CardDetailScreen.tsx's own "Publish" button is gated stricter
     * (creator only, no admin bypass) purely in the UI, not because the backend forbids it. */
    public void publishCard(String memberId, String cardId) {
        if (!ObjectId.isValid(cardId)) {
            throw new RuntimeException("Invalid cardId");
        }
        ObjectId requesterProfileId = resolveActiveProfileId(memberId);
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        authorizeCardMutation(card, requesterProfileId);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(new ObjectId(cardId))),
                new Update().set("isReadyToPublish", true),
                "cards"
        );
    }

    /**
     * Permanently deletes a card — the reference's own deleteCard: removes the card's own
     * linked Component docs (editor JSON, LINE Flex JSON, rendered HTML) along with the Card
     * document itself. Unlike setCardDeleted, this has no deleteInfo guard (matching the
     * reference exactly) — it can hard-delete a card whether or not it was soft-deleted
     * first, since the Recycle Bin's own "Delete Forever" is just the one place the RN app
     * actually calls this from. This port never introduced the reference's separate
     * `Profile.cards` array (see fetchDeletedCardsByCreator's own comment on why), so unlike
     * the reference's own deleteCard, there's no `$pull` step needed here to keep in sync.
     */
    public void deleteCardPermanently(String memberId, String cardId) {
        if (!ObjectId.isValid(cardId)) {
            throw new RuntimeException("Invalid cardId");
        }
        ObjectId requesterProfileId = resolveActiveProfileId(memberId);
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        authorizeCardMutation(card, requesterProfileId);

        List<ObjectId> componentIds = Stream.of(card.getComponents(), card.getLineFormatComponent(), card.getFlexFormatHtml())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!componentIds.isEmpty()) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").in(componentIds)), "components");
        }

        // Strip the card out of every Collection.cards array that still references it (e.g.
        // NAMECARD/CONVERSATION or any user-created collection) — otherwise those collections
        // would keep a dangling cardId pointing at a document that no longer exists.
        ObjectId cardObjectId = new ObjectId(cardId);
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("cards").is(cardObjectId)),
                new Update().pull("cards", cardObjectId),
                "collections"
        );

        cardRepository.deleteById(cardId);
    }

    /** Same memberId -> active profile resolution saveCard's own inline logic does — pulled
     * out here since setCardDeleted/deleteCardPermanently both need the exact same lookup to
     * authorize their own mutation, and inlining it twice more would just drift eventually.
     * Public (not just used internally) so ProfileService.createCollection can reuse it too,
     * rather than duplicating the exact same member-\>active-profile lookup a third time. */
    public ObjectId resolveActiveProfileId(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        List<ObjectId> profiles = member.getProfiles();
        if (profiles == null || profiles.isEmpty() || member.getActiveProfile() >= profiles.size()) {
            throw new RuntimeException("No active profile for member");
        }
        return profiles.get(member.getActiveProfile());
    }

    /** Only the card's own creator, or a FLEXADMIN, may soft-delete/restore/permanently
     * delete it — same authorization rule the reference applies identically to both of its
     * own mutation actions (updateDeleteCardStatus and deleteCard). */
    private void authorizeCardMutation(Card card, ObjectId requesterProfileId) {
        if (requesterProfileId.equals(card.getCreator())) return;
        Profile requester = profileRepository.findById(requesterProfileId.toHexString()).orElse(null);
        if (requester != null && "FLEXADMIN".equals(requester.getUsertype())) return;
        throw new SecurityException("You do not have permission to modify this card");
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

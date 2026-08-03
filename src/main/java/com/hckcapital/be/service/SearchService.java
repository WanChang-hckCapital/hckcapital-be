package com.hckcapital.be.service;

import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.SearchHistoryResponse;
import com.hckcapital.be.model.SearchHistory;
import com.hckcapital.be.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SkipOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** SearchScreen.tsx — ported from the old Next.js reference's own Searchbar.tsx +
 * searchCardForMobile/createSearchHistory/loadSearchHistory/clearSearchHistory (see
 * lib/actions/user.actions.ts / lib/models/searchhistory.ts). Card search only (not
 * profiles/forum) — searchCardForMobile is the reference's own mobile-tailored search
 * function this mirrors most directly. */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MongoTemplate mongoTemplate;
    private final SearchHistoryRepository searchHistoryRepository;
    private final CardService cardService;

    /** Same public-feed shape as CardService.fetchAllCards (isReadyToPublish, not deleted,
     * creator.accountType == "PUBLIC" or the viewer's own card), plus a case-insensitive
     * title/description match on `keyword` — mirrors the reference's own searchCardForMobile
     * (status == "Public", title/description regex), adapted to this backend's own
     * isReadyToPublish/accountType-based publicness (see fetchAllCards' own doc comment on
     * why "Public" isn't a literal field here). */
    public CardPageResponse searchCards(String keyword, int page, int limit, String viewerProfileId) {
        int skip = (page - 1) * limit;
        Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);

        MatchOperation matchPublished = Aggregation.match(
                Criteria.where("isReadyToPublish").is(true)
                        .and("deleteInfo.isDeleted").ne(true)
                        .orOperator(
                                Criteria.where("title").regex(pattern),
                                Criteria.where("description").regex(pattern)
                        )
        );

        SortOperation sort = Aggregation.sort(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id"))
        );

        LookupOperation lookupCreator = Aggregation.lookup("profiles", "creator", "_id", "creator");
        UnwindOperation unwindCreator = Aggregation.unwind("creator");

        MatchOperation matchAccess;
        boolean hasViewer = viewerProfileId != null && !viewerProfileId.isEmpty() && ObjectId.isValid(viewerProfileId);
        if (hasViewer) {
            matchAccess = Aggregation.match(new Criteria().orOperator(
                    Criteria.where("creator.accountType").is("PUBLIC"),
                    Criteria.where("creator._id").is(new ObjectId(viewerProfileId))
            ));
        } else {
            matchAccess = Aggregation.match(Criteria.where("creator.accountType").is("PUBLIC"));
        }

        SkipOperation skipOp = Aggregation.skip((long) skip);
        LimitOperation limitOp = Aggregation.limit(limit);

        LookupOperation lookupLine = Aggregation.lookup("components", "lineFormatComponent", "_id", "lineComponentData");
        UnwindOperation unwindLine = Aggregation.unwind("lineComponentData", true);
        LookupOperation lookupFlex = Aggregation.lookup("components", "flexFormatHtml", "_id", "flexHtmlData");
        UnwindOperation unwindFlex = Aggregation.unwind("flexHtmlData", true);

        AggregationOperation project = ctx -> {
            Document proj = new Document()
                    .append("title", 1).append("description", 1)
                    .append("shareCount", 1)
                    .append("comments", 1)
                    .append("likes", 1)
                    .append("cardShareTitle", 1)
                    .append("totalViews", 1)
                    .append("createdAt", 1)
                    .append("isReadyToPublish", 1)
                    .append("creator", new Document()
                            .append("_id", 1)
                            .append("accountname", 1)
                            .append("imageFilePath", 1)
                    )
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
                matchPublished, sort, lookupCreator, unwindCreator, matchAccess,
                skipOp, limitOp, lookupLine, unwindLine, lookupFlex, unwindFlex, project
        );

        List<Document> results = mongoTemplate.aggregate(aggregation, "cards", Document.class).getMappedResults();
        List<CardSummaryResponse> cards = results.stream()
                .map(cardService::mapDocument)
                .collect(Collectors.toList());

        return new CardPageResponse(cards, cards.size() == limit);
    }

    public void recordSearchHistory(String profileId, String keyword) {
        if (profileId == null || !ObjectId.isValid(profileId) || keyword == null || keyword.isBlank()) return;
        SearchHistory history = new SearchHistory();
        history.setProfileId(new ObjectId(profileId));
        history.setKeyword(keyword);
        history.setSearchAt(LocalDateTime.now());
        searchHistoryRepository.save(history);
    }

    public List<SearchHistoryResponse> loadSearchHistory(String profileId, int page, int limit) {
        if (profileId == null || !ObjectId.isValid(profileId)) return List.of();
        return searchHistoryRepository
                .findByProfileIdOrderBySearchAtDesc(new ObjectId(profileId), PageRequest.of(Math.max(0, page - 1), limit))
                .stream()
                .map(h -> new SearchHistoryResponse(h.getId(), h.getKeyword(), h.getSearchAt()))
                .collect(Collectors.toList());
    }

    public void clearSearchHistory(String profileId) {
        if (profileId == null || !ObjectId.isValid(profileId)) return;
        searchHistoryRepository.deleteByProfileId(new ObjectId(profileId));
    }
}

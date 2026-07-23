package com.hckcapital.be.service;

import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CollectionSummaryResponse;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.ProfileResponse;
import com.hckcapital.be.model.Collection;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.repository.CollectionRepository;
import com.hckcapital.be.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final CollectionRepository collectionRepository;
    private final CardService cardService;
    private final MongoTemplate mongoTemplate;

    public ProfileResponse getProfile(String profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));

        int followersCount = profile.getFollowers() != null ? profile.getFollowers().size() : 0;
        int followingCount = profile.getFollowing() != null ? profile.getFollowing().size() : 0;
        int cardCount = countPublishedCards(profileId);
        int draftCount = countDraftCards(profileId);

        return new ProfileResponse(
                profile.getId(),
                profile.getAccountname(),
                profile.getImageFilePath(),
                profile.getShortdescription(),
                profile.getUsertype(),
                profile.getAccountType(),
                profile.getRole(),
                followersCount,
                followingCount,
                cardCount,
                draftCount
        );
    }

    public List<FollowUserResponse> getFollowers(String profileId) {
        Profile profile = profileRepository.findById(profileId).orElse(null);
        if (profile == null || profile.getFollowers() == null || profile.getFollowers().isEmpty()) {
            return List.of();
        }

        List<ObjectId> followerIds = profile.getFollowers().stream()
                .map(Profile.Follower::getFollowersId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (followerIds.isEmpty()) return List.of();

        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(followerIds)),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    public List<FollowUserResponse> getFollowing(String profileId) {
        Profile profile = profileRepository.findById(profileId).orElse(null);
        if (profile == null || profile.getFollowing() == null || profile.getFollowing().isEmpty()) {
            return List.of();
        }

        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(profile.getFollowing())),
                Profile.class
        ).stream()
                .map(p -> new FollowUserResponse(p.getId(), p.getAccountname(), p.getImageFilePath()))
                .collect(Collectors.toList());
    }

    public List<CollectionSummaryResponse> getCollections(String profileId) {
        ObjectId creatorId = new ObjectId(profileId);
        return collectionRepository.findByCreatorOrderByCreatedAtAsc(creatorId).stream()
                .map(c -> new CollectionSummaryResponse(
                        c.getId(),
                        c.getName(),
                        c.getPublicStatus() != null ? c.getPublicStatus().name() : "PUBLIC",
                        c.getIsCustom() != null ? c.getIsCustom() : true,
                        c.getCards() != null ? c.getCards().size() : 0
                ))
                .collect(Collectors.toList());
    }

    public List<CardSummaryResponse> getCollectionCards(String collectionId, String viewerProfileId) {
        Collection collection = collectionRepository.findById(collectionId).orElse(null);
        if (collection == null || collection.getCards() == null || collection.getCards().isEmpty()) {
            return List.of();
        }
        return cardService.fetchCardsByIds(collection.getCards(), viewerProfileId);
    }

    public CardPageResponse getPublishedCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchCardsByCreator(new ObjectId(profileId), true, page, limit, viewerProfileId);
    }

    public CardPageResponse getDraftCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchCardsByCreator(new ObjectId(profileId), false, page, limit, viewerProfileId);
    }

    private int countPublishedCards(String profileId) {
        Query q = Query.query(
                Criteria.where("creator").is(new ObjectId(profileId))
                        .and("isReadyToPublish").is(true)
                        .and("deleteInfo.isDeleted").ne(true)
        );
        return (int) mongoTemplate.count(q, "cards");
    }

    private int countDraftCards(String profileId) {
        Query q = Query.query(
                Criteria.where("creator").is(new ObjectId(profileId))
                        .and("isReadyToPublish").ne(true)
                        .and("deleteInfo.isDeleted").ne(true)
        );
        return (int) mongoTemplate.count(q, "cards");
    }
}

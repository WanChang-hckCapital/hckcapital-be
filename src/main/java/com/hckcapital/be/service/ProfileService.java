package com.hckcapital.be.service;

import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.CollectionSummaryResponse;
import com.hckcapital.be.dto.CreateCollectionRequest;
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

import java.time.LocalDateTime;
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

    /** Creates a new custom (isCustom: true) collection under the caller's own active
     * profile — same "reject a duplicate name for this creator" rule the old Next.js
     * reference's addProfileCollections applies before inserting. `memberId` (not a
     * profileId param) is deliberate: unlike the read-only endpoints above, a mutation needs
     * to resolve the creator from the authenticated caller itself, the same way
     * CardService.saveCard does, rather than trusting a client-supplied profileId. */
    public CollectionSummaryResponse createCollection(String memberId, CreateCollectionRequest request) {
        ObjectId creatorId = cardService.resolveActiveProfileId(memberId);

        String name = requireName(request);
        if (collectionRepository.findByCreatorAndName(creatorId, name).isPresent()) {
            throw new RuntimeException("Tab name already exists.");
        }
        Collection.PublicStatus publicStatus = parsePublicStatus(request);

        Collection collection = new Collection();
        collection.setName(name);
        collection.setCreator(creatorId);
        collection.setPublicStatus(publicStatus);
        collection.setIsCustom(true);
        collection.setCards(List.of());
        LocalDateTime now = LocalDateTime.now();
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        Collection saved = collectionRepository.save(collection);

        return new CollectionSummaryResponse(saved.getId(), saved.getName(), saved.getPublicStatus().name(), true, 0);
    }

    /** Renames a collection and/or flips its public/private status — same ownership check
     * the old Next.js reference's own updateProfileCollection applies (`_id` + `creator`
     * must both match), ported here as an explicit SecurityException instead of the
     * reference's silent "not found" (see CardService.authorizeCardMutation for the same
     * pattern elsewhere in this port). Deliberately no FLEXADMIN bypass, unlike card
     * mutations — the reference never extends collection ownership to admins either, and a
     * profile's own collections aren't the kind of content moderation is meant to reach. */
    public CollectionSummaryResponse updateCollection(String memberId, String collectionId, CreateCollectionRequest request) {
        ObjectId creatorId = cardService.resolveActiveProfileId(memberId);
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        if (!creatorId.equals(collection.getCreator())) {
            throw new SecurityException("You do not have permission to modify this collection");
        }

        String name = requireName(request);
        collectionRepository.findByCreatorAndName(creatorId, name)
                .filter(existing -> !existing.getId().equals(collectionId))
                .ifPresent(existing -> {
                    throw new RuntimeException("Tab name already exists.");
                });
        Collection.PublicStatus publicStatus = parsePublicStatus(request);

        collection.setName(name);
        collection.setPublicStatus(publicStatus);
        collection.setUpdatedAt(LocalDateTime.now());
        Collection saved = collectionRepository.save(collection);

        return new CollectionSummaryResponse(
                saved.getId(),
                saved.getName(),
                saved.getPublicStatus().name(),
                saved.getIsCustom() != null ? saved.getIsCustom() : true,
                saved.getCards() != null ? saved.getCards().size() : 0
        );
    }

    private static final int COLLECTION_NAME_MAX_LENGTH = 20;

    private String requireName(CreateCollectionRequest request) {
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new RuntimeException("Collection name is required");
        }
        if (name.length() > COLLECTION_NAME_MAX_LENGTH) {
            throw new RuntimeException("Collection name must be " + COLLECTION_NAME_MAX_LENGTH + " characters or fewer");
        }
        return name;
    }

    private Collection.PublicStatus parsePublicStatus(CreateCollectionRequest request) {
        try {
            return Collection.PublicStatus.valueOf(request.getPublicStatus());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid publicStatus");
        }
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

    public CardPageResponse getRecycleBinCards(String profileId, int page, int limit, String viewerProfileId) {
        return cardService.fetchDeletedCardsByCreator(new ObjectId(profileId), page, limit, viewerProfileId);
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

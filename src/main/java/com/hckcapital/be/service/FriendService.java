package com.hckcapital.be.service;

import com.hckcapital.be.dto.FriendSummaryResponse;
import com.hckcapital.be.model.FollowRequest;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.repository.FollowRequestRepository;
import com.hckcapital.be.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final ProfileRepository profileRepository;
    private final FollowRequestRepository followRequestRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    /** All onboarded profiles except the caller (no follow status — client derives it). */
    public List<FriendSummaryResponse> getFriendList(String currentProfileId) {
        ObjectId currentObjId = new ObjectId(currentProfileId);

        Query query = Query.query(
                Criteria.where("onboarded").is(true)
                        .and("deletedAt").isNull()
                        .and("_id").ne(currentObjId)
        );
        return mongoTemplate.find(query, Profile.class).stream()
                .map(p -> new FriendSummaryResponse(
                        p.getId(), p.getAccountname(), p.getAccountType(),
                        p.getRole(), p.getImageFilePath(), false, false))
                .collect(Collectors.toList());
    }

    /** Profile IDs that currentProfileId is currently following. */
    public List<String> getFollowingIds(String currentProfileId) {
        Profile profile = profileRepository.findById(currentProfileId).orElse(null);
        if (profile == null || profile.getFollowing() == null) return List.of();
        return profile.getFollowing().stream()
                .map(ObjectId::toHexString)
                .collect(Collectors.toList());
    }

    /** Receiver profile IDs where a pending follow request was sent by currentProfileId. */
    public List<String> getSentRequestIds(String currentProfileId) {
        ObjectId senderObjId = new ObjectId(currentProfileId);
        return followRequestRepository.findBySender(senderObjId).stream()
                .filter(r -> r.getStatus() == null)   // null = pending
                .map(r -> r.getReceiver().toHexString())
                .collect(Collectors.toList());
    }

    public void followUser(String senderId, String receiverId) {
        ObjectId senderObjId = new ObjectId(senderId);
        ObjectId receiverObjId = new ObjectId(receiverId);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(senderObjId)),
                new Update().addToSet("following", receiverObjId),
                Profile.class
        );

        Document followerEntry = new Document("followersId", senderObjId)
                .append("followedAt", new Date());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(receiverObjId)),
                new Update().push("followers", followerEntry),
                Profile.class
        );

        notificationService.createNotification(
                receiverObjId, senderObjId, "PROFILE_FOLLOWED", senderObjId, "profile", null
        );
    }

    public void unfollowUser(String senderId, String receiverId) {
        ObjectId senderObjId = new ObjectId(senderId);
        ObjectId receiverObjId = new ObjectId(receiverId);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(senderObjId)),
                new Update().pull("following", receiverObjId),
                Profile.class
        );
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(receiverObjId)),
                new Update().pull("followers", new Document("followersId", senderObjId)),
                Profile.class
        );
    }

    public void sendFollowRequest(String senderId, String receiverId) {
        ObjectId senderObjId = new ObjectId(senderId);
        ObjectId receiverObjId = new ObjectId(receiverId);

        if (followRequestRepository.existsBySenderAndReceiver(senderObjId, receiverObjId)) return;

        FollowRequest request = new FollowRequest();
        request.setSender(senderObjId);
        request.setReceiver(receiverObjId);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        followRequestRepository.save(request);
    }
}

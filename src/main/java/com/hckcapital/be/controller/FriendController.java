package com.hckcapital.be.controller;

import com.hckcapital.be.dto.FriendSummaryResponse;
import com.hckcapital.be.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public ResponseEntity<List<FriendSummaryResponse>> getFriends(
            @RequestParam String profileId
    ) {
        return ResponseEntity.ok(friendService.getFriendList(profileId));
    }

    @GetMapping("/following")
    public ResponseEntity<Map<String, Object>> getFollowingIds(
            @RequestParam String profileId
    ) {
        return ResponseEntity.ok(Map.of("ids", friendService.getFollowingIds(profileId)));
    }

    @GetMapping("/requests/sent")
    public ResponseEntity<Map<String, Object>> getSentRequestIds(
            @RequestParam String profileId
    ) {
        return ResponseEntity.ok(Map.of("ids", friendService.getSentRequestIds(profileId)));
    }

    @PostMapping("/follow")
    public ResponseEntity<?> follow(@RequestBody Map<String, String> body) {
        friendService.followUser(body.get("senderId"), body.get("receiverId"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/unfollow")
    public ResponseEntity<?> unfollow(@RequestBody Map<String, String> body) {
        friendService.unfollowUser(body.get("senderId"), body.get("receiverId"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestBody Map<String, String> body) {
        friendService.sendFollowRequest(body.get("senderId"), body.get("receiverId"));
        return ResponseEntity.ok(Map.of("success", true));
    }
}

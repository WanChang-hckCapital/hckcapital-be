package com.hckcapital.be.controller;

import com.hckcapital.be.dto.AddCommentRequest;
import com.hckcapital.be.dto.CardCategoryCountResponse;
import com.hckcapital.be.dto.CardCommentResponse;
import com.hckcapital.be.dto.CardLikeToggleResponse;
import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.CardSummaryResponse;
import com.hckcapital.be.dto.FollowUserResponse;
import com.hckcapital.be.dto.SaveCardRequest;
import com.hckcapital.be.dto.SaveCardResponse;
import com.hckcapital.be.service.CardCommentService;
import com.hckcapital.be.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final CardCommentService cardCommentService;

    @GetMapping("/{cardId}")
    public ResponseEntity<CardSummaryResponse> getCard(
            @PathVariable String cardId,
            @RequestParam(required = false) String viewerProfileId
    ) {
        CardSummaryResponse card = cardService.fetchCardById(cardId, viewerProfileId);
        if (card == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(card);
    }

    @GetMapping("/{cardId}/likes")
    public ResponseEntity<List<FollowUserResponse>> getCardLiker(@PathVariable String cardId) {
        if (!ObjectId.isValid(cardId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(cardService.fetchCardLiker(new ObjectId(cardId)));
    }

    @PostMapping("/{cardId}/like")
    public ResponseEntity<CardLikeToggleResponse> toggleLike(
            @PathVariable String cardId,
            @RequestParam String profileId
    ) {
        if (!ObjectId.isValid(cardId) || !ObjectId.isValid(profileId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(cardService.toggleLike(new ObjectId(cardId), new ObjectId(profileId)));
    }

    // See CardService.recordCardView — called from CardDetailScreen.tsx when opening a
    // card. viewerProfileId is the caller's own active profile; no-ops server-side on a
    // self-view (the creator viewing their own card) or missing viewer id.
    @PostMapping("/{cardId}/view")
    public ResponseEntity<Void> recordCardView(
            @PathVariable String cardId,
            @RequestParam(required = false) String viewerProfileId
    ) {
        cardService.recordCardView(cardId, viewerProfileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cardId}/comments")
    public ResponseEntity<List<CardCommentResponse>> getComments(
            @PathVariable String cardId,
            @RequestParam(required = false) String viewerProfileId
    ) {
        if (!ObjectId.isValid(cardId)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(cardCommentService.fetchCardComments(new ObjectId(cardId), viewerProfileId));
    }

    @PostMapping("/{cardId}/comments")
    public ResponseEntity<CardCommentResponse> addComment(
            @PathVariable String cardId,
            @RequestBody AddCommentRequest request
    ) {
        if (!ObjectId.isValid(cardId)
                || request.getProfileId() == null || !ObjectId.isValid(request.getProfileId())
                || request.getComment() == null || request.getComment().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        CardCommentResponse response = cardCommentService.addComment(
                new ObjectId(cardId), new ObjectId(request.getProfileId()), request.getComment().trim()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> saveCard(Authentication authentication, @Valid @RequestBody SaveCardRequest request) {
        try {
            String memberId = (String) authentication.getPrincipal();
            SaveCardResponse response = cardService.saveCard(memberId, request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{cardId}/delete")
    public ResponseEntity<?> softDeleteCard(Authentication authentication, @PathVariable String cardId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            cardService.setCardDeleted(memberId, cardId, true);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{cardId}/restore")
    public ResponseEntity<?> restoreCard(Authentication authentication, @PathVariable String cardId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            cardService.setCardDeleted(memberId, cardId, false);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{cardId}/publish")
    public ResponseEntity<?> publishCard(Authentication authentication, @PathVariable String cardId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            cardService.publishCard(memberId, cardId);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<?> deleteCardPermanently(Authentication authentication, @PathVariable String cardId) {
        try {
            String memberId = (String) authentication.getPrincipal();
            cardService.deleteCardPermanently(memberId, cardId);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<CardPageResponse> getCards(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String profileId
    ) {
        CardPageResponse response = cardService.fetchAllCards(page, limit, profileId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/templates")
    public ResponseEntity<CardPageResponse> getTemplates(
            @RequestParam String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(cardService.fetchCardsByCategory(category, page, limit));
    }

    @GetMapping("/templates/counts")
    public ResponseEntity<List<CardCategoryCountResponse>> getTemplateCategoryCounts() {
        return ResponseEntity.ok(cardService.fetchCategoryCounts());
    }
}

package com.hckcapital.be.controller;

import com.hckcapital.be.dto.CardPageResponse;
import com.hckcapital.be.dto.SearchHistoryResponse;
import com.hckcapital.be.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SearchScreen.tsx — see SearchService for the port of the old Next.js reference's own
 * Searchbar.tsx + search history actions. */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/cards")
    public ResponseEntity<CardPageResponse> searchCards(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String viewerProfileId
    ) {
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.ok(new CardPageResponse(List.of(), false));
        }
        return ResponseEntity.ok(searchService.searchCards(keyword, page, limit, viewerProfileId));
    }

    // Called once the user actually taps into a result (see Searchbar.tsx's own
    // createSearchHistory call on result click) — not on every keystroke.
    @PostMapping("/history")
    public ResponseEntity<Void> recordSearchHistory(
            @RequestParam String profileId,
            @RequestParam String keyword
    ) {
        searchService.recordSearchHistory(profileId, keyword);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<SearchHistoryResponse>> loadSearchHistory(
            @RequestParam String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(searchService.loadSearchHistory(profileId, page, limit));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearSearchHistory(@RequestParam String profileId) {
        searchService.clearSearchHistory(profileId);
        return ResponseEntity.noContent().build();
    }
}

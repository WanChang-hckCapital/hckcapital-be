package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** SearchScreen.tsx's own history list — see SearchService.loadSearchHistory. */
@Data
@AllArgsConstructor
public class SearchHistoryResponse {
    private String id;
    private String keyword;
    private LocalDateTime searchAt;
}

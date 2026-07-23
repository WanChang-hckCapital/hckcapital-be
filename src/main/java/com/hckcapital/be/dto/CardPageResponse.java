package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CardPageResponse {
    private List<CardSummaryResponse> cards;
    private boolean hasMore;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CollectionSummaryResponse {
    private String collectionId;
    private String name;
    private String publicStatus;
    private Boolean isCustom;
    private int cardCount;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CardLikeToggleResponse {
    private boolean liked;
    private int likeCount;
}

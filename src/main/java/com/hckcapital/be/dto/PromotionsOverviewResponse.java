package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Dashboard > Promotion — see StripeService.getPromotionsOverview. Same
 * configured/errorMessage distinction as AffiliatesOverviewResponse/
 * ProductsOverviewResponse. */
@Data
@AllArgsConstructor
public class PromotionsOverviewResponse {
    private boolean configured;
    private String errorMessage;
    private List<StripePromotionCodeResponse> promotions;
}

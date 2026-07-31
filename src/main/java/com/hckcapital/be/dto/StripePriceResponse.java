package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One recurring price on a Stripe product — see StripeService.getProductsOverview.
 * `amount` is in the smallest currency unit (cents for USD), matching Stripe's own
 * unit_amount convention; the RN app divides by 100 when displaying it, same as the old
 * Next.js reference's own AdminViewPremiumProductItem.tsx. */
@Data
@AllArgsConstructor
public class StripePriceResponse {
    private String priceId;
    private Long amount;
    private String currency;
}

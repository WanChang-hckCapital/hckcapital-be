package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Mirrors the old Next.js reference's own fetchProducts (lib/actions/stripe.actions.ts) —
 * see StripeService.getProductsOverview. `featuresPremium`/`featuresFree` are parsed out of
 * the Stripe product's own `metadata.features` JSON blob (set manually in the Stripe
 * dashboard, shaped like {"premium": {"en": [...], "zh-TW": [...]}, "free": {...}}), keyed
 * by language code — the RN app picks its own current language at render time, same as the
 * reference's own getProductFeatures(product, planType, lang) did. Both maps are empty (not
 * null) when the product has no such metadata. */
@Data
@AllArgsConstructor
public class StripeProductResponse {
    private String id;
    private String name;
    private String description;
    private StripePriceResponse monthlyPrice;
    private StripePriceResponse yearlyPrice;
    private boolean freePlan;
    private Map<String, List<String>> featuresPremium;
    private Map<String, List<String>> featuresFree;
}

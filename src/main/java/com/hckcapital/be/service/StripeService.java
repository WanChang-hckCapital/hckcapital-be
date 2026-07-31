package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hckcapital.be.dto.DailySeriesPointResponse;
import com.hckcapital.be.dto.ProductsOverviewResponse;
import com.hckcapital.be.dto.PromotionsOverviewResponse;
import com.hckcapital.be.dto.StripeCouponResponse;
import com.hckcapital.be.dto.StripePriceResponse;
import com.hckcapital.be.dto.StripeProductResponse;
import com.hckcapital.be.dto.StripePromotionCodeResponse;
import com.hckcapital.be.dto.TransactionsOverviewResponse;
import com.hckcapital.be.repository.FlxBubbleConstantRepository;
import com.stripe.Stripe;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.BalanceTransactionCollection;
import com.stripe.model.Coupon;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.Product;
import com.stripe.model.ProductCollection;
import com.stripe.model.PromotionCode;
import com.stripe.model.PromotionCodeCollection;
import com.stripe.model.SubscriptionCollection;
import com.stripe.param.BalanceTransactionListParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.ProductListParams;
import com.stripe.param.PromotionCodeListParams;
import com.stripe.param.SubscriptionListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard > Product/Promotion — ported from the old Next.js reference's own
 * lib/actions/stripe.actions.ts (fetchProducts/fetchAllPromotionCodes). Same "configured"
 * gate as RewardfulService: STRIPE_SECRET_KEY not set at all means no call is attempted;
 * a set key that still fails (bad key, network) reports as `errorMessage` instead.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {

    private static final String SHOW_FREE_PLAN_KEY = "BOOLEAN_SHOW_FREE_PLAN";

    @Value("${stripe.secret-key:}")
    private String secretKey;

    private final FlxBubbleConstantRepository flxBubbleConstantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductsOverviewResponse getProductsOverview() {
        if (secretKey == null || secretKey.isBlank()) {
            return new ProductsOverviewResponse(false, null, false, List.of());
        }
        try {
            Stripe.apiKey = secretKey;

            ProductCollection productCollection = Product.list(
                    ProductListParams.builder().setActive(true).setLimit(100L).build()
            );
            PriceCollection priceCollection = Price.list(
                    PriceListParams.builder().setActive(true).setLimit(100L).build()
            );

            Map<String, List<Price>> pricesByProduct = new HashMap<>();
            for (Price price : priceCollection.getData()) {
                pricesByProduct.computeIfAbsent(price.getProduct(), k -> new ArrayList<>()).add(price);
            }

            List<StripeProductResponse> products = new ArrayList<>();
            for (Product product : productCollection.getData()) {
                List<Price> productPrices = pricesByProduct.getOrDefault(product.getId(), List.of());

                Price monthly = productPrices.stream()
                        .filter(p -> p.getRecurring() != null && "month".equals(p.getRecurring().getInterval()))
                        .findFirst().orElse(null);
                Price yearly = productPrices.stream()
                        .filter(p -> p.getRecurring() != null && "year".equals(p.getRecurring().getInterval()))
                        .findFirst().orElse(null);

                StripePriceResponse monthlyPrice = monthly == null ? null
                        : new StripePriceResponse(monthly.getId(), monthly.getUnitAmount(), monthly.getCurrency());
                StripePriceResponse yearlyPrice = yearly == null ? null
                        : new StripePriceResponse(yearly.getId(), yearly.getUnitAmount(), yearly.getCurrency());

                Map<String, List<String>> featuresPremium = parseFeatures(product.getMetadata(), "premium");
                Map<String, List<String>> featuresFree = parseFeatures(product.getMetadata(), "free");

                products.add(new StripeProductResponse(
                        product.getId(), product.getName(), product.getDescription(),
                        monthlyPrice, yearlyPrice, monthly == null && yearly == null,
                        featuresPremium, featuresFree
                ));
            }

            return new ProductsOverviewResponse(true, null, shouldShowFreePlan(), products);
        } catch (Exception e) {
            log.warn("Stripe products API call failed", e);
            return new ProductsOverviewResponse(true, e.getMessage(), false, List.of());
        }
    }

    public PromotionsOverviewResponse getPromotionsOverview() {
        if (secretKey == null || secretKey.isBlank()) {
            return new PromotionsOverviewResponse(false, null, List.of());
        }
        try {
            Stripe.apiKey = secretKey;

            PromotionCodeCollection promotionCodeCollection = PromotionCode.list(
                    PromotionCodeListParams.builder().setLimit(100L).addExpand("data.coupon").build()
            );

            List<StripePromotionCodeResponse> promotions = new ArrayList<>();
            for (PromotionCode promotionCode : promotionCodeCollection.getData()) {
                Coupon coupon = promotionCode.getCoupon();
                StripeCouponResponse couponResponse = coupon == null ? null : new StripeCouponResponse(
                        coupon.getId(), coupon.getName(),
                        coupon.getPercentOff() != null ? coupon.getPercentOff().longValue() : null,
                        coupon.getAmountOff(), coupon.getCurrency(), coupon.getDuration(),
                        coupon.getDurationInMonths(), coupon.getTimesRedeemed(), coupon.getMaxRedemptions()
                );
                promotions.add(new StripePromotionCodeResponse(
                        promotionCode.getId(), promotionCode.getCode(),
                        Boolean.TRUE.equals(promotionCode.getActive()), couponResponse
                ));
            }

            return new PromotionsOverviewResponse(true, null, promotions);
        } catch (Exception e) {
            log.warn("Stripe promotion codes API call failed", e);
            return new PromotionsOverviewResponse(true, e.getMessage(), List.of());
        }
    }

    /** Mirrors the old Next.js reference's own transactions page.tsx (computeStats/
     * calcGrowth) + TransactionChart.tsx, but period-scoped via Settings > Report's own
     * PeriodPicker (see ReportSection.tsx/TransactionsSection.tsx) instead of the
     * reference's hardcoded trailing-7-days stats / unbounded all-time chart fetch.
     * `startDate`/`endDate` are inclusive; growth compares this period against the
     * equivalent-length period immediately before it (same "mirror the previous window"
     * approach as ProfileService.getReportOverview's own previousCardCount etc). */
    public TransactionsOverviewResponse getTransactionsOverview(LocalDate startDate, LocalDate endDate) {
        if (secretKey == null || secretKey.isBlank()) {
            return new TransactionsOverviewResponse(false, null, 0, 0, 0, 0, List.of());
        }
        try {
            Stripe.apiKey = secretKey;

            long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            LocalDate previousEnd = startDate.minusDays(1);
            LocalDate previousStart = previousEnd.minusDays(periodDays - 1);

            BalanceStats currentStats = computeBalanceStats(startDate, endDate);
            BalanceStats previousStats = computeBalanceStats(previousStart, previousEnd);

            double transactionGrowth = calcGrowth(currentStats.count(), previousStats.count());
            double feeGrowth = calcGrowth(currentStats.fees(), previousStats.fees());

            List<DailySeriesPointResponse> revenueSeries = computeRevenueSeries(startDate, endDate);

            return new TransactionsOverviewResponse(
                    true, null, currentStats.count(), currentStats.fees(),
                    transactionGrowth, feeGrowth, revenueSeries
            );
        } catch (Exception e) {
            log.warn("Stripe transactions API call failed", e);
            return new TransactionsOverviewResponse(true, e.getMessage(), 0, 0, 0, 0, List.of());
        }
    }

    private record BalanceStats(int count, double fees) {
    }

    /** Dashboard > Dashboard (overview)'s "Total Subscriptions" chart — see
     * AdminService.getOverview. Mirrors the old Next.js reference's own
     * fetchActiveSubscriptions + SubscriptionByDayChart: a cumulative count of every
     * currently-*active* Stripe subscription, bucketed by its own creation day. Unlike the
     * reference's own unbounded all-time fetch, this is bounded to [startDate, endDate] —
     * same deliberate scoping as getTransactionsOverview — but still seeds the running
     * total with every active subscription created before startDate, so the first plotted
     * point is the real cumulative total on that day, not just "new since the range
     * began" (same seed-then-walk shape as ProfileService.getFollowersSeries). */
    public ActiveSubscriptionsSeriesResult getActiveSubscriptionsSeries(LocalDate startDate, LocalDate endDate) {
        if (secretKey == null || secretKey.isBlank()) {
            return new ActiveSubscriptionsSeriesResult(false, null, List.of());
        }
        try {
            Stripe.apiKey = secretKey;

            SubscriptionListParams params = SubscriptionListParams.builder()
                    .setStatus(SubscriptionListParams.Status.ACTIVE)
                    .setLimit(100L)
                    .build();
            SubscriptionCollection collection = com.stripe.model.Subscription.list(params);

            long seed = 0;
            Map<String, Integer> newByDay = new HashMap<>();
            LocalDate rangeStart = startDate;
            LocalDate rangeEnd = endDate;
            for (com.stripe.model.Subscription sub : collection.autoPagingIterable()) {
                LocalDate createdDate = Instant.ofEpochSecond(sub.getCreated()).atZone(ZoneOffset.UTC).toLocalDate();
                if (createdDate.isBefore(rangeStart)) {
                    seed++;
                } else if (!createdDate.isAfter(rangeEnd)) {
                    newByDay.merge(createdDate.format(DateTimeFormatter.ISO_LOCAL_DATE), 1, Integer::sum);
                }
            }

            List<DailySeriesPointResponse> series = new ArrayList<>();
            long cumulative = seed;
            LocalDate cursor = rangeStart;
            while (!cursor.isAfter(rangeEnd)) {
                String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
                cumulative += newByDay.getOrDefault(key, 0);
                series.add(new DailySeriesPointResponse(key, (int) cumulative));
                cursor = cursor.plusDays(1);
            }

            return new ActiveSubscriptionsSeriesResult(true, null, series);
        } catch (Exception e) {
            log.warn("Stripe active subscriptions API call failed", e);
            return new ActiveSubscriptionsSeriesResult(true, e.getMessage(), List.of());
        }
    }

    public record ActiveSubscriptionsSeriesResult(
            boolean configured, String errorMessage, List<DailySeriesPointResponse> series
    ) {
    }

    private static long startOfDayEpoch(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private static long endOfDayEpoch(LocalDate date) {
        return date.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC);
    }

    /** Every balance transaction type in the window (charges, refunds, fees, adjustments —
     * unfiltered, same as the reference's own computeStats(transactions), which counts
     * `transactions.length` with no type check). */
    private BalanceStats computeBalanceStats(LocalDate start, LocalDate end) throws Exception {
        BalanceTransactionListParams params = BalanceTransactionListParams.builder()
                .setLimit(100L)
                .setCreated(BalanceTransactionListParams.Created.builder()
                        .setGte(startOfDayEpoch(start)).setLte(endOfDayEpoch(end)).build())
                .build();
        BalanceTransactionCollection collection = BalanceTransaction.list(params);

        int count = 0;
        long feesCents = 0;
        for (BalanceTransaction tx : collection.autoPagingIterable()) {
            count++;
            feesCents += tx.getFee() != null ? tx.getFee() : 0;
        }
        return new BalanceStats(count, feesCents / 100.0);
    }

    /** Same formula as the reference's own calcGrowth: 100% "growth" when going from zero
     * to something, 0% when staying at zero. */
    private double calcGrowth(double current, double previous) {
        if (previous == 0) return current > 0 ? 100 : 0;
        return ((current - previous) / previous) * 100;
    }

    /** Only "charge" type transactions count toward revenue — matches the reference's own
     * TransactionChart.tsx, which skips every other balance transaction type when building
     * this chart (refunds/fees/adjustments aren't "revenue"). Every day in the window is
     * present (0-filled), not just days with a charge, so the chart draws a continuous
     * line — same convention as ProfileService's own daily series. `count` on each point is
     * actually that day's total charge amount in cents, not a literal count (see
     * TransactionsOverviewResponse's own doc comment). */
    private List<DailySeriesPointResponse> computeRevenueSeries(LocalDate start, LocalDate end) throws Exception {
        BalanceTransactionListParams params = BalanceTransactionListParams.builder()
                .setLimit(100L)
                .setType("charge")
                .setCreated(BalanceTransactionListParams.Created.builder()
                        .setGte(startOfDayEpoch(start)).setLte(endOfDayEpoch(end)).build())
                .build();
        BalanceTransactionCollection collection = BalanceTransaction.list(params);

        Map<String, Long> revenueCentsByDate = new HashMap<>();
        for (BalanceTransaction tx : collection.autoPagingIterable()) {
            String date = Instant.ofEpochSecond(tx.getCreated()).atZone(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            long amount = tx.getAmount() != null ? tx.getAmount() : 0;
            revenueCentsByDate.merge(date, amount, Long::sum);
        }

        List<DailySeriesPointResponse> series = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
            long cents = revenueCentsByDate.getOrDefault(key, 0L);
            series.add(new DailySeriesPointResponse(key, (int) cents));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /** Mirrors the reference's own shouldShowFreePlan — defaults to true when the flag
     * document doesn't exist at all (same "show it unless explicitly turned off" default). */
    private boolean shouldShowFreePlan() {
        return flxBubbleConstantRepository.findByKey(SHOW_FREE_PLAN_KEY)
                .map(c -> Boolean.TRUE.equals(c.getValue()))
                .orElse(true);
    }

    /** Stripe product metadata stores features as a JSON string:
     * {"premium": {"en": [...], "zh-TW": [...]}, "free": {"en": [...], "zh-TW": [...]}} —
     * mirrors the reference's own getProductFeatures, but returns every language at once
     * (keyed by language code) rather than resolving a single one server-side, since the RN
     * app already knows its own current language at render time. */
    private Map<String, List<String>> parseFeatures(Map<String, String> metadata, String planType) {
        if (metadata == null) return Collections.emptyMap();
        String raw = metadata.get("features");
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        try {
            JsonNode root = objectMapper.readTree(raw).path(planType);
            if (!root.isObject()) return Collections.emptyMap();
            Map<String, List<String>> result = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                List<String> values = new ArrayList<>();
                entry.getValue().forEach(v -> values.add(v.asText()));
                result.put(entry.getKey(), values);
            });
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}

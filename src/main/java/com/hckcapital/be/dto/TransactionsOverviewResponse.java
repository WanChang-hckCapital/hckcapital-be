package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Dashboard > Transaction — see StripeService.getTransactionsOverview. Same
 * configured/errorMessage distinction as the other Dashboard sections.
 * `totalTransactions`/`totalFees` cover the caller-selected period (see
 * TransactionsSection.tsx's own PeriodPicker); `*GrowthPercent` compares that period
 * against the equivalent-length period immediately before it (mirrors the old Next.js
 * reference's own computeStats/calcGrowth, generalized from that reference's hardcoded
 * 7-vs-7-days). `revenueSeries` reuses DailySeriesPointResponse (date, count) from the
 * Report screen's own line graphs — here `count` is actually that day's total "charge"
 * revenue in cents (not a literal count), so the RN app divides by 100 the same way it
 * already does for StripePriceResponse.amount. */
@Data
@AllArgsConstructor
public class TransactionsOverviewResponse {
    private boolean configured;
    private String errorMessage;
    private int totalTransactions;
    private double totalFees;
    private double transactionGrowthPercent;
    private double feeGrowthPercent;
    private List<DailySeriesPointResponse> revenueSeries;
}

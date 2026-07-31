package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Dashboard > Dashboard (overview) — see AdminService.getOverview. Ported from the old
 * Next.js reference's own app/[lang]/(dashboard)/dashboard/page.tsx.
 *
 * `weeklyRevenue`/`monthlyRevenue` (and their growth %) are always "this week vs last
 * week" / "this month vs last month" — fixed windows, same as the reference's own
 * hardcoded stat cards, NOT scoped by the caller-supplied startDate/endDate. Both are
 * summed straight from the local `subscriptions` collection (Subscription.totalAmount),
 * so — unlike the Transaction section's Stripe-backed numbers — these are always real,
 * no configured/errorMessage gate needed.
 *
 * `newMembersSeries`/`cumulativeMembersSeries`/`cumulativePersonalProfilesSeries`/
 * `cumulativeOrganizationProfilesSeries` ARE scoped to the caller's startDate/endDate —
 * one shared OverviewSection.tsx PeriodPicker drives all four, rather than the reference's
 * own five independently-selectable per-chart ranges (same simplification already applied
 * to the Transaction section's single PeriodPicker vs. the reference's own per-chart
 * ChartCard controls).
 *
 * `cumulativeSubscriptionsSeries` mirrors the reference's own "Total Subscriptions" chart
 * (SubscriptionByDayChart, fed by stripe.actions.ts's fetchActiveSubscriptions) — it's the
 * one series here backed by a live Stripe API call rather than local data, so it carries
 * its own configured/errorMessage pair instead of failing the whole response.
 *
 * `membersByCountry` is all-time, not period-scoped — mirrors the reference's own
 * MembersCountryTypeChart, which is fed the unbounded member list rather than a date range.
 */
@Data
@AllArgsConstructor
public class AdminOverviewResponse {
    private double weeklyRevenue;
    private double lastWeekRevenue;
    private double weeklyGrowthPercent;

    private double monthlyRevenue;
    private double lastMonthRevenue;
    private double monthlyGrowthPercent;

    private List<DailySeriesPointResponse> newMembersSeries;
    private List<DailySeriesPointResponse> cumulativeMembersSeries;
    private List<DailySeriesPointResponse> cumulativePersonalProfilesSeries;
    private List<DailySeriesPointResponse> cumulativeOrganizationProfilesSeries;

    private boolean subscriptionsConfigured;
    private String subscriptionsErrorMessage;
    private List<DailySeriesPointResponse> cumulativeSubscriptionsSeries;

    private List<MemberCountryBreakdownResponse> membersByCountry;
}

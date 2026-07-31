package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One country's share of all registered Members — see AdminService.getMembersByCountry.
 * Sorted by `count` descending; members whose Member.country is unset land in an "Unknown"
 * bucket rather than being silently dropped from the total. All-time, not period-scoped —
 * mirrors the old Next.js reference's own MembersCountryTypeChart, which is fed the
 * unbounded fetchAllMember() result rather than any date-ranged query. */
@Data
@AllArgsConstructor
public class MemberCountryBreakdownResponse {
    private String country;
    private int count;
    private double percentage;
}

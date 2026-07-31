package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One day's worth of a Report screen line graph — see ProfileService.getProfileViewsSeries
 * (daily view counts) and getFollowersSeries (cumulative follower total), both of which
 * return this same shape since it's what every day-by-day chart on this screen needs.
 * `date` is "yyyy-MM-dd"; every day in the requested range is present, not just days with
 * activity, so the chart draws a continuous line instead of skipping gaps. */
@Data
@AllArgsConstructor
public class DailySeriesPointResponse {
    private String date;
    private int count;
}

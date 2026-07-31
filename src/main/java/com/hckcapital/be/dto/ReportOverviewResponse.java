package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** See ProfileService.getReportOverview — Settings > Report's own "Overview" tab. Likes/
 * comments are real (summed from each of the caller's own cards' actual `likes`/`comments`
 * arrays), though only approximated by period — see getReportOverview's own doc comment.
 * `profileViewsCount` and `cardViewsCount` are both real and precisely period-scoped, each
 * backed by an indexed range query against its own event collection (ProfileView /
 * CardView respectively) rather than any embedded array on Profile/Card.
 *
 * `previous*` fields mirror the current period's own counts, but for the equivalent-length
 * period immediately before it (e.g. "last 7 days" vs. the 7 days before that) — powers
 * the Report screen's progress-bar/delta indicators. They're only meaningful (non-zero)
 * when the request supplied both a startDate and endDate; an open-ended or all-time query
 * has no well-defined period to mirror, so they're left at 0 in that case. */
@Data
@AllArgsConstructor
public class ReportOverviewResponse {
    private int cardCount;
    private int draftCount;
    private int followersCount;
    private int followingCount;
    private int totalLikes;
    private int totalComments;
    private int profileViewsCount;
    private int cardViewsCount;
    private int previousCardCount;
    private int previousDraftCount;
    private int previousProfileViewsCount;
    private int previousCardViewsCount;
}

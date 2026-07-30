package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** See ProfileService.getReportOverview — Settings > Report's own "Overview" tab. Likes/
 * comments are real (summed from each of the caller's own cards' actual `likes`/`comments`
 * arrays). `profileViewsCount` is real too, backed by Profile.viewDetails — see
 * ProfileService.recordProfileView, ported from the old Next.js reference's
 * updateProfileViewData (self-views skipped, repeat views from the same viewer within 3
 * minutes deduped). Card.totalViews/viewDetails, by contrast, are still never incremented
 * anywhere in this backend, which is why there is no per-card views number here. */
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
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** One row of Dashboard > Member's list — see AdminService.getMemberList. Ported from the
 * old Next.js reference's own fetchProfilesPaginated + columns.tsx (ProfileListType).
 * `cardsCount` is Profile.cards.size(), not a separate query. */
@Data
@AllArgsConstructor
public class MemberSummaryResponse {
    private String profileId;
    private String accountname;
    private String email;
    private String imageFilePath;
    private String usertype;
    private int cardsCount;
    private Boolean onboarded;
    private LocalDateTime createdAt;
}

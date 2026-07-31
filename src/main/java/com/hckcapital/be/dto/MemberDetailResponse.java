package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Dashboard > Member's detail sheet — see AdminService.getMemberDetail. Ported from the
 * old Next.js reference's own fetchProfileDetails: `usertype`/`accountType`/`onboarded`/
 * `referralCode`/`bubblePoint`/`stripeCustomerId` come straight off the Profile document;
 * `country` is a reverse lookup onto the owning Member (Profile itself has no country
 * field — see Member.profiles' own reverse-lookup doc comment on CardService), same as
 * CardService.getCardViewCountryBreakdown's own country resolution. */
@Data
@AllArgsConstructor
public class MemberDetailResponse {
    private String usertype;
    private String accountType;
    private Boolean onboarded;
    private String referralCode;
    private int bubblePoint;
    private String country;
    private String stripeCustomerId;
}

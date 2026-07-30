package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** See ProfileService.getAccountDetails — Settings > Manage's own read-only email/country/
 * phone display. Sourced from Member (not Profile — see Member.java's own email/country/
 * countrycode/phone fields, set at signup/onboarding), and only ever reachable via the
 * caller's own JWT (unlike GET /api/v1/profile, which takes an arbitrary profileId and is
 * used to view other people's profiles too — these fields must never leak through that
 * public path). */
@Data
@AllArgsConstructor
public class AccountDetailsResponse {
    private String email;
    private String country;
    private String countrycode;
    private String phone;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileResponse {
    private String profileId;
    private String accountname;
    private String imageFilePath;
    private String shortdescription;
    private String usertype;
    private String accountType;
    private String role;
    private int followersCount;
    private int followingCount;
    private int cardCount;
    private int draftCount;
}

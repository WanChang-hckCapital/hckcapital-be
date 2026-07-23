package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendSummaryResponse {
    private String profileId;
    private String accountname;
    private String accountType;
    private String role;
    private String imageFilePath;
    private boolean isFollowing;
    private boolean requestSent;
}

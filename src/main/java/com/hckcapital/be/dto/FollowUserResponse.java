package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FollowUserResponse {
    private String profileId;
    private String accountname;
    private String imageFilePath;
}

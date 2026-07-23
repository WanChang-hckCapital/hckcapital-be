package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String memberId;
    private String email;
    private String profileId;
    private String name;
    private String userType;
    private boolean onboarded;
    private boolean hasSubscription;
    private String profileImage;
}

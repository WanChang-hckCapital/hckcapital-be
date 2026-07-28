package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OnboardResponse {
    private String accountname;
    private String shortdescription;
    private String imageFilePath;
    private boolean onboarded;
}

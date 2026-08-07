package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubscriptionActionResponse {
    private boolean success;
    private String message;
}

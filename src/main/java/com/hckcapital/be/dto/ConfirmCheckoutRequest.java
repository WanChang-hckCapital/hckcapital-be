package com.hckcapital.be.dto;

import lombok.Data;

@Data
public class ConfirmCheckoutRequest {
    private String sessionId;
    private String subscriptionId;
}

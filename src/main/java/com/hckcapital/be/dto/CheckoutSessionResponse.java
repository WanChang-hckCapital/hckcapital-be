package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutSessionResponse {
    private boolean success;
    private String url;
    private String subscriptionId;
    private String errorMessage;
}

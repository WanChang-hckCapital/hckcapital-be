package com.hckcapital.be.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "subscriptions")
public class Subscription {

    @Id
    private String id;

    private String status;

    private LocalDateTime estimatedEndDate;

    // Used by AdminService's weekly/monthly revenue stats — mirrors the Mongoose
    // reference's own subscriptionSchema (planStarted/totalAmount), not previously ported
    // since nothing on this side needed them until now.
    private LocalDateTime planStarted;

    private double totalAmount;
}

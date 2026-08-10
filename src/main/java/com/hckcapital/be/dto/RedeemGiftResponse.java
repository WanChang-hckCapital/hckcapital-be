package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /api/v1/redeem/gifts — see RedeemService.listGifts. Mirrors RedeemGift's own fields
 * (see that model's doc comment) — this is a straight read-through, no derived fields. */
@Data
@AllArgsConstructor
public class RedeemGiftResponse {
    private String id;
    private String category;
    private String name;
    private Double marketValue;
    private int pointsRequired;
    private Integer daysRequiredText;
    private Integer subscriptionDays;
    private String description;
    private String tag;
    private boolean isHot;
    private int stock;
}

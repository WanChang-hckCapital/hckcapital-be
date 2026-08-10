package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/v1/redeem — see RedeemService.redeem. `isVip`/`subscriptionEndDate` are set
 * only when the redeemed gift's own category is "platform" (VIP-days grant) — see that
 * method's own doc comment for the exact Subscription-creation mechanism this mirrors from
 * the reference's own redeemGiftPoints. */
@Data
@AllArgsConstructor
public class RedeemResponse {
    private boolean success;
    private String message;
    private Integer newPoints;
    private Integer newStock;
    private boolean isVip;
    private String subscriptionEndDate;
}

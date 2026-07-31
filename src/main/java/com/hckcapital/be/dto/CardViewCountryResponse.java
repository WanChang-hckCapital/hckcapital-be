package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One country's share of a card's distinct viewers — see
 * CardService.getCardViewCountryBreakdown. Sorted by `count` descending; viewers whose
 * Member.country is unset land in an "Unknown" bucket rather than being silently dropped
 * from the total. */
@Data
@AllArgsConstructor
public class CardViewCountryResponse {
    private String country;
    private int count;
    private double percentage;
}

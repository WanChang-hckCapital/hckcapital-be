package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** GET /api/v1/profile/points — see ProfileService.getPointsHistory. `currentPoints` is the
 * most recent log's own `afterPoints`, matching the reference's own loadPersonalPoints
 * exactly (NOT Profile.bubblePoint directly, though in practice they should always agree
 * since every bubblePoint write here always pairs with a PointsLog row — see this file's
 * own doc comment for why that distinction still matters as a fallback). */
@Data
@AllArgsConstructor
public class PointsHistoryResponse {
    private int currentPoints;
    private List<PointsLogEntryResponse> logs;
}

package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** GET /api/v1/profile/missions?period=daily|weekly — see MissionService.getMissions.
 * `currentPoints` is included here too (not just on the points-history endpoint) so the
 * Missions tab's own balance banner can render without a second round trip, same as the
 * reference's screenshot always showing the balance card regardless of which tab is active. */
@Data
@AllArgsConstructor
public class MissionsResponse {
    private int currentPoints;
    private List<MissionSlotResponse> missions;
}

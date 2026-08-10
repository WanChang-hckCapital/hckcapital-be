package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Shared response shape for check-in and claim-reward — see MissionService.checkIn/
 * claimReward. `newPoints` is null on failure (already checked in today, mission not yet
 * completed, reward already claimed, etc. — `message` carries the reason). */
@Data
@AllArgsConstructor
public class MissionActionResponse {
    private boolean success;
    private String message;
    private Integer newPoints;
}

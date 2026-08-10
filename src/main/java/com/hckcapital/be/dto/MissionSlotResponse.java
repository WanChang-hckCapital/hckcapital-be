package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One row of GET /api/v1/profile/missions — see MissionService.MISSION_CONFIG for where
 * `target`/`points` come from (a hardcoded config, same as the reference's own
 * MISSION_CONFIG, not stored per-row in Mongo — only progress/completed/rewardClaimed are
 * actually persisted, in MissionProgress). */
@Data
@AllArgsConstructor
public class MissionSlotResponse {
    private String missionType;
    private String period;
    private int progress;
    private int target;
    private int points;
    private boolean completed;
    private boolean rewardClaimed;
}

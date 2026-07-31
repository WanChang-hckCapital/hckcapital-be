package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Mirrors the fields the old Next.js reference's own dashboard/affiliates page actually
 * displays from Rewardful's campaign response (id/name/state) — see RewardfulService,
 * which parses Rewardful's raw (snake_case) JSON into this plain DTO manually rather than
 * relying on Jackson's automatic (camelCase) deserialization, same style as
 * MetadataService's own JsonNode-tree-walking. */
@Data
@AllArgsConstructor
public class RewardfulCampaignResponse {
    private String id;
    private String name;
    private String state;
}

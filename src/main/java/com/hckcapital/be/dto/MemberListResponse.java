package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** See AdminService.getMemberList — `total` is the full matching count (unpaginated), so
 * the RN app's MemberSection.tsx knows when it's reached the last page. */
@Data
@AllArgsConstructor
public class MemberListResponse {
    private List<MemberSummaryResponse> data;
    private int total;
}

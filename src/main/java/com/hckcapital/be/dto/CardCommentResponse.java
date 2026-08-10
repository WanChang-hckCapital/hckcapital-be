package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Date;

/** `missionProgress`/`primeTimeMissionProgress` are only ever populated on the response to
 * an actual new comment being posted (see CardCommentService.addComment), never on the plain
 * fetchCardComments list read — mirrors CardLikeToggleResponse's own doc comment on the same
 * pattern for likes, letting the RN app's MissionProgressToast render straight off this
 * response instead of a second request. */
@Data
@AllArgsConstructor
public class CardCommentResponse {
    private String commentID;
    private String comment;

    private String commenterId;
    private String commenterName;
    private String commenterImage;

    private int likeCount;
    private boolean isLikedByMe;

//    private List<CardCommentResponse> replies;

    private Date commentDate;

    private IncrementMissionResult missionProgress;
    private IncrementMissionResult primeTimeMissionProgress;
}

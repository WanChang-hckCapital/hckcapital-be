package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Date;

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
}

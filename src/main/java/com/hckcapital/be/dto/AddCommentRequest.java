package com.hckcapital.be.dto;

import lombok.Data;

@Data
public class AddCommentRequest {
    private String profileId;
    private String comment;
}

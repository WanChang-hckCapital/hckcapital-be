package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class NotificationPageResponse {
    private List<NotificationResponse> notifications;
    private boolean hasMore;
}

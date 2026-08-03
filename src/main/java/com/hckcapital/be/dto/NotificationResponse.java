package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** One row of NotificationScreen.tsx's own feed — see NotificationService.getUserNotifications.
 * `senderAccountName`/`senderImage` are resolved server-side (a lookup onto Profile) so the
 * RN app never has to fetch the sender separately per row. */
@Data
@AllArgsConstructor
public class NotificationResponse {
    private String id;
    private String senderUserId;
    private String senderAccountName;
    private String senderImage;
    private String notificationType;
    private boolean read;
    private String targetId;
    private String targetType;
    private Map<String, Object> relatedData;
    private LocalDateTime createdAt;
}

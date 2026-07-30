package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** See ProfileService.getNotificationSettings/updateNotificationSettings — Settings >
 * Notification. Mirrors Profile.Notifications (email/inApp), field names matching the old
 * Next.js reference project's own NotificationSettings type (types/notifications/index.ts). */
@Data
@AllArgsConstructor
public class NotificationSettingsResponse {
    private Boolean emailNotification;
    private Boolean inAppNotification;
}

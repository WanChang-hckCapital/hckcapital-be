package com.hckcapital.be.dto;

import lombok.Data;

/** See ProfileService.updateNotificationSettings. Both fields optional — the caller only
 * sends whichever one it's actually changing; omitted (null) fields are left untouched.
 * `inAppNotification` is accepted here for schema parity with the old Next.js reference
 * project's own updateInAppNotification action, but the RN Settings screen never actually
 * sends it — see NotificationSettingsSection.tsx's own doc comment on why that toggle stays
 * disabled, matching the reference's own (seemingly deliberate) UI. */
@Data
public class UpdateNotificationSettingsRequest {
    private Boolean emailNotification;
    private Boolean inAppNotification;
}

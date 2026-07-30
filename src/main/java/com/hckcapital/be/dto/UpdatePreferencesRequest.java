package com.hckcapital.be.dto;

import lombok.Data;

import java.util.List;

/** See ProfileService.updatePreferences. Every field is optional — the caller only sends
 * whatever it's actually changing (theme/language save immediately on toggle in
 * SettingsScreen.tsx; `categories`/`isSkip` save together via a batch "Save" button, same
 * split as the old Next.js reference project's own updateProfileThemePreference/
 * updateUserLanguagePreference/saveProfilePreferences — three separate actions there, one
 * flexible endpoint here). Omitted (null) fields are left untouched. */
@Data
public class UpdatePreferencesRequest {
    private String theme;
    private String language;
    private List<String> categories;
    private Boolean isSkip;
}

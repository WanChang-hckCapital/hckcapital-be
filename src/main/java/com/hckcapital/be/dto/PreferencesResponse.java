package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** See ProfileService.getPreferences/updatePreferences — the Settings > Theme/Language/
 * Preference screens' own shared read shape, mirroring Profile.Preferences. */
@Data
@AllArgsConstructor
public class PreferencesResponse {
    private String theme;
    private String language;
    private List<String> categories;
    private Boolean isSkip;
}

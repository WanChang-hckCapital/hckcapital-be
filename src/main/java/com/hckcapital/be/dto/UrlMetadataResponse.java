package com.hckcapital.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Scraped Open Graph metadata for a single URL — powers the "Shortcut" (paste a URL, get a
 * card) feature; see MetadataService.fetchMetadata. */
@Data
@AllArgsConstructor
public class UrlMetadataResponse {
    private String title;
    private String description;
    private String image;
    private String url;
}

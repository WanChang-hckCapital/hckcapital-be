package com.hckcapital.be.controller;

import com.hckcapital.be.dto.UrlMetadataResponse;
import com.hckcapital.be.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/read")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestParam(required = false) String url) {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        try {
            UrlMetadataResponse metadata = metadataService.fetchMetadata(url);
            return ResponseEntity.ok(metadata);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

package com.hckcapital.be.controller;

import com.hckcapital.be.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Uploads hero videos for the card editor's Add Video feature — same GCS bucket/service
 * account and upload mechanics as ImageController (see ImageUploadService, which is
 * generic enough to serve both), but with its own 100MB cap, matching the old Next.js
 * reference project's app/api/v1/videos/route.ts (`MAX_VIDEO_BYTES = 100 * 1024 * 1024`,
 * enforced there too, alongside the client-side checks in VideoModal.tsx/
 * ComponentTreeStructure.tsx's handleUploadVideo). The reference's duration limits
 * (30s free / 5min subscribed, admin exempt) aren't re-checked here — like the reference,
 * that's only ever validated client-side (see AddVideoModal.tsx), not something this
 * endpoint can verify from the file alone without extra tooling.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;

    private final ImageUploadService imageUploadService;

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("videoFile") MultipartFile videoFile,
            @RequestParam(value = "directoryPath", required = false) String directoryPath
    ) {
        if (videoFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoFile is required"));
        }

        if (videoFile.getSize() > MAX_VIDEO_BYTES) {
            return ResponseEntity.status(413).body(Map.of("error", "File exceeds 100MB limit"));
        }

        String path = (directoryPath == null || directoryPath.isBlank())
                ? "cards/video/" + UUID.randomUUID()
                : directoryPath;

        try {
            ImageUploadService.UploadResult result = imageUploadService.upload(videoFile, path);
            return ResponseEntity.ok(Map.of("url", result.url()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Video upload failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload video"));
        }
    }
}

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

@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageUploadService imageUploadService;

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam(value = "directoryPath", required = false) String directoryPath
    ) {
        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "imageFile is required"));
        }

        String path = (directoryPath == null || directoryPath.isBlank())
                ? "cards/image/" + UUID.randomUUID()
                : directoryPath;

        try {
            String url = imageUploadService.upload(imageFile, path);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Image upload failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload image"));
        }
    }
}

package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Sends a single push notification via Expo's own push service
 * (https://exp.host/--/api/v2/push/send) — the only "real" OS-level delivery path in this
 * whole notification system (see NotificationService's own doc comment on why: the durable
 * feed lives in Mongo and is fine to just poll while the app is open, but a
 * backgrounded/killed app needs an actual APNs/FCM push, which Expo's service proxies to
 * without this backend needing its own Apple/Google certificates). Only called for a
 * recipient RedisPresenceService says is NOT currently online — no point pushing to a phone
 * already looking at the app. */
@Service
@Slf4j
public class ExpoPushService {

    private static final String PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String expoPushToken, String title, String body, Map<String, Object> data) {
        if (expoPushToken == null || expoPushToken.isBlank()) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", expoPushToken);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("sound", "default");
            if (data != null) payload.put("data", data);

            HttpRequest request = HttpRequest.newBuilder(URI.create(PUSH_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Expo push send failed: {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            // Best-effort — a failed push should never block the notification itself from
            // being recorded (it's already saved to Mongo by this point).
            log.warn("Expo push send error", e);
        }
    }
}

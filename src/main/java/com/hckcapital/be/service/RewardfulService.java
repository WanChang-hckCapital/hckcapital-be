package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hckcapital.be.dto.AffiliatesOverviewResponse;
import com.hckcapital.be.dto.RewardfulAffiliateResponse;
import com.hckcapital.be.dto.RewardfulCampaignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Dashboard > Affiliates — ported from the old Next.js reference's own
 * lib/actions/rewardful.actions.ts (getRewardfulCampaignsId/getRewardfulAffiliatesByCampaign).
 * Rewardful is a third-party affiliate-tracking SaaS the reference project already has an
 * account with; this reuses that same account by reading the same REWARDFUL_API_SECRET env
 * var name (see application.properties). Raw JSON is walked manually via Jackson's JsonNode
 * (same style as MetadataService's own YouTube oEmbed parsing) rather than relying on
 * Jackson's automatic camelCase deserialization, since Rewardful's own response fields are
 * snake_case.
 */
@Service
@Slf4j
public class RewardfulService {

    private static final String BASE_URL = "https://api.getrewardful.com/v1";

    @Value("${rewardful.api-secret:}")
    private String apiSecret;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Matches the reference page's own behavior: takes the first campaign returned, then
     * lists that campaign's affiliates. `configured=false` (no API call attempted at all)
     * when the secret isn't set; `errorMessage` set instead if the secret exists but the
     * Rewardful API call itself failed. */
    public AffiliatesOverviewResponse getAffiliatesOverview() {
        if (apiSecret == null || apiSecret.isBlank()) {
            return new AffiliatesOverviewResponse(false, null, null, List.of());
        }
        try {
            List<RewardfulCampaignResponse> campaigns = fetchCampaigns();
            if (campaigns.isEmpty()) {
                return new AffiliatesOverviewResponse(true, null, null, List.of());
            }
            String campaignId = campaigns.get(0).getId();
            List<RewardfulAffiliateResponse> affiliates = fetchAffiliatesByCampaign(campaignId);
            return new AffiliatesOverviewResponse(true, null, campaignId, affiliates);
        } catch (Exception e) {
            log.warn("Rewardful API call failed", e);
            return new AffiliatesOverviewResponse(true, e.getMessage(), null, List.of());
        }
    }

    private List<RewardfulCampaignResponse> fetchCampaigns() throws Exception {
        JsonNode root = objectMapper.readTree(get(BASE_URL + "/campaigns"));
        List<RewardfulCampaignResponse> result = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            result.add(new RewardfulCampaignResponse(
                    node.path("id").asText(null),
                    node.path("name").asText(null),
                    node.path("state").asText(null)
            ));
        }
        return result;
    }

    private List<RewardfulAffiliateResponse> fetchAffiliatesByCampaign(String campaignId) throws Exception {
        String url = BASE_URL + "/affiliates?campaign_id=" + campaignId;
        JsonNode root = objectMapper.readTree(get(url));
        List<RewardfulAffiliateResponse> result = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            result.add(new RewardfulAffiliateResponse(
                    node.path("id").asText(null),
                    node.path("first_name").asText(null),
                    node.path("last_name").asText(null),
                    node.path("email").asText(null),
                    node.path("state").asText(null),
                    node.path("visitors").asInt(0),
                    node.path("leads").asInt(0),
                    node.path("conversions").asInt(0)
            ));
        }
        return result;
    }

    private String get(String url) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString((apiSecret + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Rewardful API error: " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }
}

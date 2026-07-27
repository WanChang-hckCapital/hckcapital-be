package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hckcapital.be.dto.UrlMetadataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes Open Graph metadata for the "Shortcut" feature — paste a URL, get back a
 * title/description/image the card editor can turn into a card (see
 * MetadataController/CardEditorScreenContent's own ShortcutScreen equivalent). Ported from
 * the old Next.js reference's app/api/v1/read/metadata/route.ts, simplified: the reference
 * special-cases Shopee/Facebook/Amazon by proxying through the third-party microlink.io API
 * (needs its own account/API key, not available in this environment) — those are left to
 * fall through to the same generic og:-tag scrape everyone else gets here, which still
 * recovers a usable title/image for most product/article pages. YouTube's oEmbed special
 * case is kept as-is since it needs no API key and og:-tags alone are a poor substitute for
 * a video (no reliable thumbnail).
 */
@Service
@Slf4j
public class MetadataService {

    private static final Pattern META_TAG_PATTERN = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR_PATTERN = Pattern.compile("content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UrlMetadataResponse fetchMetadata(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        String host = uri.getHost().toLowerCase();

        if (host.contains("youtube.com") || host.contains("youtu.be")) {
            try {
                return fetchYoutubeMetadata(rawUrl);
            } catch (Exception e) {
                log.warn("YouTube oEmbed failed for {}, falling back to generic scrape", rawUrl, e);
            }
        }
        return fetchGenericMetadata(rawUrl);
    }

    private UrlMetadataResponse fetchYoutubeMetadata(String url) throws IOException, InterruptedException {
        String oembedUrl = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&format=json";
        String json = httpGetString(oembedUrl);
        JsonNode node = objectMapper.readTree(json);
        String title = node.path("title").asText(null);
        String image = node.path("thumbnail_url").asText(null);
        return new UrlMetadataResponse(title, null, image, url);
    }

    private UrlMetadataResponse fetchGenericMetadata(String url) {
        String html;
        try {
            html = httpGetString(url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch URL: " + e.getMessage(), e);
        }

        String title = firstNonBlank(
                extractMetaContent(html, "property", "og:title"),
                extractMetaContent(html, "name", "twitter:title"),
                extractTitleTag(html)
        );
        String description = firstNonBlank(
                extractMetaContent(html, "property", "og:description"),
                extractMetaContent(html, "name", "description")
        );
        String image = firstNonBlank(
                extractMetaContent(html, "property", "og:image"),
                extractMetaContent(html, "name", "twitter:image")
        );
        return new UrlMetadataResponse(title, description, image, url);
    }

    private String httpGetString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                // A default Java UA gets blocked/served a stripped page by a lot of sites —
                // spoofing a real browser UA matches what the reference's own scraper does.
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Request failed with status " + response.statusCode());
        }
        return response.body();
    }

    private String extractMetaContent(String html, String attrName, String attrValue) {
        Pattern attrPattern = Pattern.compile(attrName + "\\s*=\\s*[\"']" + Pattern.quote(attrValue) + "[\"']", Pattern.CASE_INSENSITIVE);
        Matcher tagMatcher = META_TAG_PATTERN.matcher(html);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            if (attrPattern.matcher(tag).find()) {
                Matcher contentMatcher = CONTENT_ATTR_PATTERN.matcher(tag);
                if (contentMatcher.find()) {
                    return unescapeHtml(contentMatcher.group(1).trim());
                }
            }
        }
        return null;
    }

    private String extractTitleTag(String html) {
        Matcher matcher = TITLE_TAG_PATTERN.matcher(html);
        if (matcher.find()) {
            return unescapeHtml(matcher.group(1).trim());
        }
        return null;
    }

    private String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /** Only http/https, and rejects the obvious private/loopback host ranges (localhost,
     * 127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16) so this
     * server-side fetch can't be pointed at internal infrastructure — a minimal SSRF
     * guard the reference's own JSDOM-based scraper never had at all. Not exhaustive
     * (doesn't resolve DNS to catch rebinding), but blocks the trivial cases. */
    private URI parseAndValidate(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (Exception e) {
            throw new RuntimeException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new RuntimeException("Only http/https URLs are supported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new RuntimeException("Invalid URL");
        }
        if (isPrivateOrLocalHost(host.toLowerCase())) {
            throw new RuntimeException("This URL cannot be fetched");
        }
        return uri;
    }

    private boolean isPrivateOrLocalHost(String host) {
        if (host.equals("localhost") || host.equals("0.0.0.0") || host.startsWith("127.")) return true;
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return true;
        if (host.startsWith("172.")) {
            String[] parts = host.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {
                    // not a numeric octet — not a literal 172.x IP, fall through
                }
            }
        }
        return false;
    }
}

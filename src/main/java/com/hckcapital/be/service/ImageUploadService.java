package com.hckcapital.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Uploads files to Google Cloud Storage for the card editor's Add Image and Add Video
 * features (see ImageController and VideoController — this service is generic over the
 * file itself, so both share it rather than duplicating the GCS/credential plumbing) — the
 * same bucket ("flxbubble-bucket") and service account the old Next.js reference project's
 * app/api/v1/images/route.ts and app/api/v1/videos/route.ts already use, read from the same
 * SERVICE_ACCOUNT_*_BUCKET / NEXT_PUBLIC_PUBLIC_BUCKET_NAME / NEXT_PUBLIC_PROJECT_ENVIRONMENT
 * .env fields those projects defined (see application.properties). Unlike those routes
 * (which return only {success:true}/reconstruct the public URL client-side from the bucket
 * name + directoryPath), this returns the final hosted URL directly.
 *
 * The service account is stored as individual .env fields, not a JSON key file, so
 * standard GOOGLE_APPLICATION_CREDENTIALS (which expects a file path) doesn't apply here —
 * buildCredentials() reconstructs real service-account JSON from those fields instead.
 */
@Service
public class ImageUploadService {

    @Value("${gcs.bucket-name:}")
    private String bucketName;

    // Old reference project's own env var name — reused as-is rather than introducing a
    // parallel one, since this .env already carries the value under this name.
    @Value("${gcs.project-environment:}")
    private String projectEnvironment;

    @Value("${gcs.service-account-type:}")
    private String saType;
    @Value("${gcs.service-account-project-id:}")
    private String saProjectId;
    @Value("${gcs.service-account-private-key-id:}")
    private String saPrivateKeyId;
    @Value("${gcs.service-account-private-key:}")
    private String saPrivateKey;
    @Value("${gcs.service-account-client-email:}")
    private String saClientEmail;
    @Value("${gcs.service-account-client-id:}")
    private String saClientId;
    @Value("${gcs.service-account-auth-uri:}")
    private String saAuthUri;
    @Value("${gcs.service-account-token-uri:}")
    private String saTokenUri;
    @Value("${gcs.service-account-auth-provider-cert-url:}")
    private String saAuthProviderCertUrl;
    @Value("${gcs.service-account-client-cert-url:}")
    private String saClientCertUrl;
    @Value("${gcs.service-account-universe-domain:}")
    private String saUniverseDomain;

    private volatile Storage storage;

    private Storage storage() throws IOException {
        // Lazily created (not a @Bean) so missing/incomplete service-account fields don't
        // fail application startup — only the first actual upload attempt.
        if (storage == null) {
            synchronized (this) {
                if (storage == null) {
                    storage = StorageOptions.newBuilder()
                            .setCredentials(buildCredentials())
                            .build()
                            .getService();
                }
            }
        }
        return storage;
    }

    /** Reassembles the service-account JSON Google's own client libraries normally read
     * from a key file, field by field, from .env. Built via a Map + ObjectMapper (not
     * string concatenation) so the private key's embedded newlines are escaped correctly
     * regardless of whether they arrive as real newline characters or literal two-character
     * "\n" sequences — the .env file quotes the key with literal \n escapes, and different
     * dotenv parsers handle that differently, so both are normalized to real newlines
     * before serializing. */
    private GoogleCredentials buildCredentials() throws IOException {
        if (saPrivateKey == null || saPrivateKey.isBlank() || saClientEmail == null || saClientEmail.isBlank()) {
            throw new IllegalStateException("GCS service account is not configured (SERVICE_ACCOUNT_*_BUCKET env vars)");
        }

        Map<String, String> serviceAccount = new LinkedHashMap<>();
        serviceAccount.put("type", saType);
        serviceAccount.put("project_id", saProjectId);
        serviceAccount.put("private_key_id", saPrivateKeyId);
        serviceAccount.put("private_key", saPrivateKey.replace("\\n", "\n"));
        serviceAccount.put("client_email", saClientEmail);
        serviceAccount.put("client_id", saClientId);
        serviceAccount.put("auth_uri", saAuthUri);
        serviceAccount.put("token_uri", saTokenUri);
        serviceAccount.put("auth_provider_x509_cert_url", saAuthProviderCertUrl);
        serviceAccount.put("client_x509_cert_url", saClientCertUrl);
        serviceAccount.put("universe_domain", saUniverseDomain);

        String json = new ObjectMapper().writeValueAsString(serviceAccount);
        return GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    public String upload(MultipartFile file, String directoryPath) throws IOException {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Image upload is not configured (bucket name is unset)");
        }

        // Mirrors the old reference project's `${env}/${directoryPath}` object-path
        // convention, so uploads from both stacks land in the same place in the shared
        // bucket — the RN app's own gcsImageUrl() helper (config/api.ts) already assumes
        // this "staging/..." prefix for other images in this bucket.
        String prefix = (projectEnvironment == null || projectEnvironment.isBlank()) ? "" : projectEnvironment + "/";
        String objectName = prefix + directoryPath + "/" + UUID.randomUUID() + extensionFor(file);
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                .build();

        storage().create(blobInfo, file.getBytes());

        return "https://storage.googleapis.com/" + bucketName + "/" + objectName;
    }

    /** Extension is derived from the uploaded file itself, never trusted client input used
     * directly in the object name beyond that — the crop step in AddImageModal.tsx always
     * saves as JPEG, so this is mostly for pasted/forwarded files of other formats. */
    private String extensionFor(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.'));
        }
        String contentType = file.getContentType();
        if ("image/png".equals(contentType)) return ".png";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
    }
}

package com.hckcapital.be.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/** Same AWS SES setup the old Next.js reference project's own transactional emails use
 * (@aws-sdk/client-ses) — shared by AuthService.forgotPassword and OtpService.sendOtp
 * rather than each standing up its own SesClient. */
@Service
public class SesEmailService {

    @Value("${aws.access-key-id:}")
    private String awsAccessKeyId;
    @Value("${aws.secret-access-key:}")
    private String awsSecretAccessKey;
    @Value("${aws.region:}")
    private String awsRegion;

    private SesClient sesClient;

    @PostConstruct
    private void init() {
        if (awsAccessKeyId.isBlank() || awsSecretAccessKey.isBlank() || awsRegion.isBlank()) return;
        sesClient = SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)))
                .build();
    }

    public boolean isConfigured() {
        return sesClient != null;
    }

    public void sendHtml(String fromAddress, String toAddress, String subject, String htmlBody) {
        if (sesClient == null) {
            throw new RuntimeException("SES configuration missing");
        }
        sesClient.sendEmail(SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(toAddress).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().html(Content.builder().data(htmlBody).build()).build())
                        .build())
                .build());
    }

    public void sendText(String fromAddress, String toAddress, String subject, String textBody) {
        if (sesClient == null) {
            throw new RuntimeException("SES configuration missing");
        }
        sesClient.sendEmail(SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(toAddress).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(textBody).build()).build())
                        .build())
                .build());
    }
}

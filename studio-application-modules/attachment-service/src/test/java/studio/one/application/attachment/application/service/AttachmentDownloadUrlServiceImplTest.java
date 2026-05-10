package studio.one.application.attachment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.util.UriComponentsBuilder;

import studio.one.application.attachment.application.error.AttachmentDownloadTokenInvalidException;
import studio.one.application.attachment.application.error.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.application.result.AttachmentDownloadTokenClaims;
import studio.one.application.attachment.application.result.AttachmentDownloadTokenInspection;
import studio.one.application.attachment.application.result.AttachmentDownloadTokenInspectionStatus;
import studio.one.application.attachment.application.result.AttachmentDownloadUrl;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlIssueActor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.domain.port.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.infrastructure.persistence.model.AttachmentDownloadUrlIssueAuditLog;

class AttachmentDownloadUrlServiceImplTest {

    private static final String SECRET = "test-only-signing-secret-value-32b";
    private static final Instant NOW = Instant.parse("2026-05-05T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void issueDownloadUrlCreatesApplicationSignedUrlAndAuditWithoutStorageMetadata() throws Exception {
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        Attachment attachment = attachment();
        AttachmentDownloadUrlServiceImpl service = service(auditRepository, CLOCK);

        AttachmentDownloadUrl result = service.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                new AttachmentDownloadUrlIssueActor(7L, "user-7"),
                "127.0.0.1",
                "JUnit");

        assertThat(result.url()).startsWith("https://app.example/api/attachments/signed-download?token=");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-05-05T00:05:00Z"));
        String token = token(result.url());
        AttachmentDownloadTokenClaims claims = service.verifyDownloadToken(token);
        assertThat(claims.attachmentId()).isEqualTo(11L);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(result.expiresAt());
        assertThat(claims.nonce()).isNotBlank();

        ArgumentCaptor<AttachmentDownloadUrlIssueAuditLog> captor =
                ArgumentCaptor.forClass(AttachmentDownloadUrlIssueAuditLog.class);
        verify(auditRepository).save(captor.capture());
        AttachmentDownloadUrlIssueAuditLog log = captor.getValue();
        assertThat(log.getEndpointKind()).isEqualTo("SERVICE");
        assertThat(log.getTtlSeconds()).isEqualTo(300L);
        assertThat(log.getLinkType()).isEqualTo("APPLICATION_SIGNED");
        assertThat(log.getTokenHash()).isEqualTo(sha256(token));
        assertThat(log.getStorageProviderId()).isNull();
        assertThat(log.getBucket()).isNull();
        assertThat(log.getObjectKeyHash()).isNull();
        assertThat(log.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(log.getUserAgent()).isEqualTo("JUnit");
        verify(attachment, never()).getProperties();
    }

    @Test
    void invalidTtlIsRejectedBeforeAudit() {
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        AttachmentDownloadUrlServiceImpl service = service(auditRepository, CLOCK);

        assertThrows(IllegalArgumentException.class, () -> service.issueDownloadUrl(
                attachment(),
                0L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));

        verify(auditRepository, never()).save(Mockito.any());
    }

    @Test
    void invalidConfigurationRejectsIssuingWithoutBreakingConstruction() {
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        Attachment attachment = attachment();

        AttachmentDownloadUrlServiceImpl relativeBaseUrl = new AttachmentDownloadUrlServiceImpl(
                "localhost:8080",
                SECRET,
                "/api/attachments",
                auditRepository,
                CLOCK);
        AttachmentDownloadUrlServiceImpl queryBaseUrl = new AttachmentDownloadUrlServiceImpl(
                "https://app.example?from=config",
                SECRET,
                "/api/attachments",
                auditRepository,
                CLOCK);
        AttachmentDownloadUrlServiceImpl shortSecret = new AttachmentDownloadUrlServiceImpl(
                "https://app.example",
                "short",
                "/api/attachments",
                auditRepository,
                CLOCK);

        assertThrows(AttachmentDownloadUrlUnavailableException.class, () -> relativeBaseUrl.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));
        assertThrows(AttachmentDownloadUrlUnavailableException.class, () -> queryBaseUrl.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));
        assertThrows(AttachmentDownloadUrlUnavailableException.class, () -> shortSecret.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));
        assertThrows(AttachmentDownloadTokenInvalidException.class, () -> shortSecret.verifyDownloadToken("token"));
        verify(auditRepository, never()).save(Mockito.any());
    }

    @Test
    void invalidTokensAreRejected() {
        AttachmentDownloadUrlServiceImpl service =
                service(Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class), CLOCK);
        AttachmentDownloadUrl issued = service.issueDownloadUrl(
                attachment(),
                60L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null);
        String token = token(issued.url());
        String[] segments = token.split("\\.");

        assertThrows(AttachmentDownloadTokenInvalidException.class, () -> service.verifyDownloadToken(null));
        assertThrows(AttachmentDownloadTokenInvalidException.class, () -> service.verifyDownloadToken("not-a-token"));
        assertThrows(AttachmentDownloadTokenInvalidException.class,
                () -> service.verifyDownloadToken(tamperPayload(segments)));
        assertThrows(AttachmentDownloadTokenInvalidException.class,
                () -> service.verifyDownloadToken(segments[0] + "." + segments[1] + "a"));
        assertThrows(AttachmentDownloadTokenInvalidException.class,
                () -> service.verifyDownloadToken(signedToken("""
                        purpose=other
                        attachmentId=11
                        issuedAt=2026-05-05T00:00:00Z
                        expiresAt=2026-05-05T00:01:00Z
                        nonce=test
                        """.stripTrailing())));
    }

    @Test
    void expiredTokenIsRejected() {
        AttachmentDownloadUrlServiceImpl issuer =
                service(Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class), CLOCK);
        AttachmentDownloadUrl issued = issuer.issueDownloadUrl(
                attachment(),
                1L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null);
        AttachmentDownloadUrlServiceImpl verifier = service(
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        assertThrows(AttachmentDownloadTokenInvalidException.class,
                () -> verifier.verifyDownloadToken(token(issued.url())));
    }

    @Test
    void inspectDownloadTokenDistinguishesExpiredAndInvalidTokens() throws Exception {
        AttachmentDownloadUrlServiceImpl issuer =
                service(Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class), CLOCK);
        AttachmentDownloadUrl issued = issuer.issueDownloadUrl(
                attachment(),
                1L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null);
        String token = token(issued.url());
        AttachmentDownloadUrlServiceImpl verifier = service(
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        AttachmentDownloadTokenInspection expired = verifier.inspectDownloadToken(token);
        AttachmentDownloadTokenInspection invalid = verifier.inspectDownloadToken("bad-token");

        assertThat(expired.status()).isEqualTo(AttachmentDownloadTokenInspectionStatus.EXPIRED);
        assertThat(expired.claims().attachmentId()).isEqualTo(11L);
        assertThat(expired.tokenHash()).isEqualTo(sha256(token));
        assertThat(invalid.status()).isEqualTo(AttachmentDownloadTokenInspectionStatus.INVALID_TOKEN);
        assertThat(invalid.claims()).isNull();
        assertThat(invalid.tokenHash()).isEqualTo(sha256("bad-token"));
    }

    @Test
    void inspectDownloadTokenDoesNotHashOversizedToken() {
        AttachmentDownloadUrlServiceImpl verifier =
                service(Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class), CLOCK);

        AttachmentDownloadTokenInspection invalid = verifier.inspectDownloadToken("a".repeat(4097));

        assertThat(invalid.status()).isEqualTo(AttachmentDownloadTokenInspectionStatus.INVALID_TOKEN);
        assertThat(invalid.tokenHash()).isNull();
    }

    private AttachmentDownloadUrlServiceImpl service(
            AttachmentDownloadUrlIssueAuditLogRepository auditRepository,
            Clock clock) {
        return new AttachmentDownloadUrlServiceImpl(
                "https://app.example",
                SECRET,
                "/api/attachments",
                auditRepository,
                clock);
    }

    private Attachment attachment() {
        Attachment attachment = Mockito.mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(11L);
        when(attachment.getObjectType()).thenReturn(12);
        when(attachment.getObjectId()).thenReturn(34L);
        return attachment;
    }

    private static String token(String url) {
        return UriComponentsBuilder.fromUri(URI.create(url))
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private static String tamperPayload(String[] segments) {
        String payload = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace("attachmentId=11", "attachmentId=12");
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + segments[1];
    }

    private static String signedToken(String payload) throws Exception {
        String payloadSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signatureSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payloadSegment.getBytes(StandardCharsets.US_ASCII)));
        return payloadSegment + "." + signatureSegment;
    }

    private static String sha256(String token) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}

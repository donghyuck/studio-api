package studio.one.application.attachment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.util.StringUtils;

import studio.one.application.attachment.exception.AttachmentDownloadTokenInvalidException;

final class AttachmentDownloadTokenCodec {

    static final String PURPOSE = "attachment-download";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final int NONCE_BYTES = 16;
    private static final int TOKEN_MAX_LENGTH = 4096;
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Clock clock;
    private final SecureRandom secureRandom;

    AttachmentDownloadTokenCodec(String signingSecret, Clock clock) {
        this(signingSecret, clock, new SecureRandom());
    }

    AttachmentDownloadTokenCodec(String signingSecret, Clock clock, SecureRandom secureRandom) {
        if (!StringUtils.hasText(signingSecret)) {
            throw new IllegalStateException("studio.attachment.download-url.signing-secret must be configured");
        }
        byte[] bytes = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "studio.attachment.download-url.signing-secret must be at least 32 UTF-8 bytes");
        }
        this.secret = bytes.clone();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.secureRandom = secureRandom == null ? new SecureRandom() : secureRandom;
    }

    String issue(long attachmentId, Instant issuedAt, Instant expiresAt) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("purpose", PURPOSE);
        payload.put("attachmentId", Long.toString(attachmentId));
        payload.put("issuedAt", issuedAt.toString());
        payload.put("expiresAt", expiresAt.toString());
        payload.put("nonce", nonce());

        String payloadSegment = BASE64_URL_ENCODER.encodeToString(serialize(payload).getBytes(StandardCharsets.UTF_8));
        String signatureSegment = BASE64_URL_ENCODER.encodeToString(hmac(payloadSegment));
        return payloadSegment + "." + signatureSegment;
    }

    AttachmentDownloadTokenClaims verify(String token) {
        AttachmentDownloadTokenInspection inspection = inspect(token);
        if (inspection.status() != AttachmentDownloadTokenInspectionStatus.VALID) {
            throw new AttachmentDownloadTokenInvalidException();
        }
        return inspection.claims();
    }

    AttachmentDownloadTokenInspection inspect(String token) {
        String tokenHash = tokenHashForAudit(token);
        if (!StringUtils.hasText(token) || token.length() > TOKEN_MAX_LENGTH) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
        String[] segments = token.split("\\.", -1);
        if (segments.length != 2 || !StringUtils.hasText(segments[0]) || !StringUtils.hasText(segments[1])) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }

        byte[] signature;
        try {
            signature = BASE64_URL_DECODER.decode(segments[1]);
        } catch (IllegalArgumentException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
        if (!MessageDigest.isEqual(hmac(segments[0]), signature)) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }

        Map<String, String> payload;
        try {
            payload = parsePayload(segments[0]);
        } catch (AttachmentDownloadTokenInvalidException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
        if (!PURPOSE.equals(payload.get("purpose"))) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
        try {
            long attachmentId = Long.parseLong(required(payload, "attachmentId"));
            Instant issuedAt = Instant.parse(required(payload, "issuedAt"));
            Instant expiresAt = Instant.parse(required(payload, "expiresAt"));
            String nonce = required(payload, "nonce");
            if (attachmentId <= 0 || !expiresAt.isAfter(issuedAt)) {
                return AttachmentDownloadTokenInspection.invalid(tokenHash);
            }
            AttachmentDownloadTokenClaims claims =
                    new AttachmentDownloadTokenClaims(attachmentId, issuedAt, expiresAt, nonce);
            if (!expiresAt.isAfter(clock.instant())) {
                return AttachmentDownloadTokenInspection.expired(claims, tokenHash);
            }
            return AttachmentDownloadTokenInspection.valid(claims, tokenHash);
        } catch (AttachmentDownloadTokenInvalidException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        } catch (RuntimeException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
    }

    String sha256Hex(String value) {
        return sha256HexValue(value);
    }

    static String sha256HexValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static String tokenHashForAudit(String token) {
        if (!StringUtils.hasText(token) || token.length() > TOKEN_MAX_LENGTH) {
            return null;
        }
        return sha256HexValue(token);
    }

    private Map<String, String> parsePayload(String payloadSegment) {
        String payloadText;
        try {
            payloadText = new String(BASE64_URL_DECODER.decode(payloadSegment), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new AttachmentDownloadTokenInvalidException();
        }

        Map<String, String> payload = new HashMap<>();
        for (String line : payloadText.split("\\n", -1)) {
            int index = line.indexOf('=');
            if (index <= 0 || index == line.length() - 1) {
                throw new AttachmentDownloadTokenInvalidException();
            }
            String key = line.substring(0, index);
            String value = line.substring(index + 1);
            if (payload.putIfAbsent(key, value) != null) {
                throw new AttachmentDownloadTokenInvalidException();
            }
        }
        return payload;
    }

    private String required(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (!StringUtils.hasText(value)) {
            throw new AttachmentDownloadTokenInvalidException();
        }
        return value;
    }

    private String serialize(Map<String, String> payload) {
        return payload.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String nonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    private byte[] hmac(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payloadSegment.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }
}

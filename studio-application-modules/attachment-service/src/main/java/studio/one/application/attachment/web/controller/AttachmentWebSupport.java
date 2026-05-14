package studio.one.application.attachment.web.controller;

import java.io.InputStream;
import java.time.Instant;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.application.attachment.application.command.AttachmentDownloadAuditLogCommand;
import studio.one.application.attachment.application.result.AttachmentDownloadAuditResult;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlIssueActor;
import studio.one.application.attachment.web.dto.response.AttachmentDto;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.identity.UserDto;
import studio.one.platform.identity.UserRef;
import studio.one.platform.web.dto.ApiResponse;

final class AttachmentWebSupport {

    private static final Logger log = LoggerFactory.getLogger(AttachmentWebSupport.class);
    static final long MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024;

    private AttachmentWebSupport() {
    }

    static PreparedUpload prepareUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new IllegalArgumentException("File too large");
        }
        if (file.getSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("File size exceeds supported limit");
        }
        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(sanitizedName)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return new PreparedUpload(sanitizedName, resolveMediaTypeString(file.getContentType()), (int) file.getSize());
    }

    static ResponseEntity<StreamingResponseBody> downloadResponse(
            Attachment attachment,
            InputStream in,
            CacheControl cacheControl) {
        StreamingResponseBody body = out -> {
            try (in) {
                in.transferTo(out);
            }
        };
        return downloadResponse(attachment, body, cacheControl);
    }

    static ResponseEntity<StreamingResponseBody> auditedDownloadResponse(
            Attachment attachment,
            InputStream in,
            CacheControl cacheControl,
            String linkType,
            Instant requestedAt,
            AttachmentUrlIssueRequestDetails details,
            Consumer<AttachmentDownloadAuditLogCommand> auditRecorder) {
        StreamingResponseBody body = out -> {
            long downloadedBytes = 0L;
            byte[] buffer = new byte[8192];
            try (in) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloadedBytes += read;
                }
                auditRecorder.accept(downloadAuditCommand(
                        attachment,
                        attachment.getAttachmentId(),
                        linkType,
                        requestedAt,
                        AttachmentDownloadAuditResult.SUCCEEDED,
                        HttpStatus.OK.value(),
                        downloadedBytes,
                        details,
                        null));
            } catch (java.io.IOException | RuntimeException ex) {
                auditRecorder.accept(downloadAuditCommand(
                        attachment,
                        attachment.getAttachmentId(),
                        linkType,
                        requestedAt,
                        AttachmentDownloadAuditResult.FAILED,
                        HttpStatus.OK.value(),
                        downloadedBytes,
                        details,
                        "STREAM_FAILED"));
                throw ex;
            }
        };
        return downloadResponse(attachment, body, cacheControl);
    }

    static AttachmentDownloadAuditLogCommand downloadAuditCommand(
            Attachment attachment,
            Long fallbackAttachmentId,
            String linkType,
            Instant requestedAt,
            AttachmentDownloadAuditResult result,
            int httpStatus,
            Long downloadedBytes,
            AttachmentUrlIssueRequestDetails details,
            String errorCode) {
        return new AttachmentDownloadAuditLogCommand(
                null,
                attachment == null ? fallbackAttachmentId : attachment.getAttachmentId(),
                attachment == null ? null : attachment.getObjectType(),
                attachment == null ? null : attachment.getObjectId(),
                linkType,
                requestedAt,
                result,
                httpStatus,
                downloadedBytes,
                details == null ? null : details.clientIp(),
                details == null ? null : details.userAgent(),
                errorCode);
    }

    private static ResponseEntity<StreamingResponseBody> downloadResponse(
            Attachment attachment,
            StreamingResponseBody body,
            CacheControl cacheControl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(cacheControl.getHeaderValue());
        headers.setContentType(resolveMediaType(attachment.getContentType()));
        headers.setContentLength(attachment.getSize());
        if (StringUtils.hasText(attachment.getName())) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(attachment.getName())
                    .build());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    static AttachmentDto toDto(Attachment attachment, ObjectProvider<IdentityService> identityServiceProvider) {
        return AttachmentDto.of(
                attachment,
                findUserDto(identityServiceProvider, attachment.getCreatedBy(), attachment.getAttachmentId(), log));
    }

    static MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    static String resolveMediaTypeString(String contentType) {
        return resolveMediaType(contentType).toString();
    }

    static String sanitizeFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return null;
        }
        String cleaned = org.springframework.util.StringUtils.getFilename(original.replace("\\", "/"));
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        return cleaned.replace("\\", "").replace("/", "");
    }

    static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        ApiResponse<T> body = ApiResponse.<T>builder()
                .message(message)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        ApiResponse<T> body = ApiResponse.<T>builder()
                .message(message)
                .build();
        return ResponseEntity.status(409).body(body);
    }

    static AttachmentDownloadUrlIssueActor auditActor(ApplicationPrincipal principal) {
        if (principal == null) {
            return null;
        }
        return new AttachmentDownloadUrlIssueActor(principal.getUserId(), principalName(principal));
    }

    static AttachmentDownloadUrlIssueActor auditActor(ObjectProvider<PrincipalResolver> principalResolverProvider) {
        PrincipalResolver resolver = principalResolverProvider == null ? null : principalResolverProvider.getIfAvailable();
        ApplicationPrincipal principal = resolver == null ? null : resolver.currentOrNull();
        if (principal != null) {
            return auditActor(principal);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return new AttachmentDownloadUrlIssueActor(null, authentication.getName());
    }

    private static UserDto findUserDto(
            ObjectProvider<IdentityService> identityServiceProvider,
            long userId,
            long attachmentId,
            Logger log) {
        if (userId <= 0) {
            return null;
        }
        IdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            return null;
        }
        return identityService.findById(userId)
                .map(AttachmentWebSupport::toUserDto)
                .orElseGet(() -> {
                    log.warn("User {} not found for attachment {}", userId, attachmentId);
                    return null;
                });
    }

    private static UserDto toUserDto(UserRef userRef) {
        return new UserDto(userRef.userId(), userRef.username());
    }

    private static String principalName(ApplicationPrincipal principal) {
        if (StringUtils.hasText(principal.getUsername())) {
            return principal.getUsername();
        }
        Long userId = principal.getUserId();
        return userId == null ? null : String.valueOf(userId);
    }

    static final class PreparedUpload {
        private final String name;
        private final String contentType;
        private final int sizeBytes;

        PreparedUpload(String name, String contentType, int sizeBytes) {
            this.name = name;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
        }

        String name() { return name; }

        String contentType() { return contentType; }

        int sizeBytes() { return sizeBytes; }
    }
}

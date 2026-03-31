package studio.one.application.web.controller;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.identity.IdentityService;
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

    record PreparedUpload(String name, String contentType, int sizeBytes) {
    }
}

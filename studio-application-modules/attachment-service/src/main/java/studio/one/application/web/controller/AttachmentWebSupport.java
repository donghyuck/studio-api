package studio.one.application.web.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.web.dto.ApiResponse;

final class AttachmentWebSupport {

    private AttachmentWebSupport() {
    }

    static String sanitizeFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return null;
        }
        String cleaned = StringUtils.getFilename(original);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        return cleaned.replace("\\", "").replace("/", "");
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

    static HttpHeaders downloadHeaders(String contentType, long size, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setContentType(resolveMediaType(contentType));
        headers.setContentLength(size);
        if (StringUtils.hasText(filename)) {
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        }
        return headers;
    }

    static HttpHeaders thumbnailHeaders(String contentType, long size) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.maxAge(3600, TimeUnit.SECONDS).getHeaderValue());
        headers.setContentType(resolveMediaType(contentType));
        headers.setContentLength(size);
        return headers;
    }

    static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        ApiResponse<T> body = ApiResponse.<T>builder()
                .message(message)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    static boolean isAdmin(ApplicationPrincipal principal) {
        return principal != null && (principal.hasRole("ADMIN") || principal.hasRole("ROLE_ADMIN"));
    }

    static long requireUserId(ApplicationPrincipal principal) {
        if (principal != null && principal.getUserId() != null && principal.getUserId() > 0) {
            return principal.getUserId();
        }
        throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                "No authenticated user");
    }

    static long requireUserId(Long userId) {
        if (userId != null && userId > 0) {
            return userId;
        }
        throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                "No authenticated user");
    }
}

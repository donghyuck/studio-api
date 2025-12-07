package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.User;

public abstract class AbstractAvatarController {

    protected User toUser(Long userId) {
        ApplicationUser user = new ApplicationUser();
        user.setUserId(userId);
        return user;
    }

    protected ResponseEntity<StreamingResponseBody> notAavaliable() throws IOException {
        ClassPathResource resource = new ClassPathResource("assets/images/no-avatar.png");
        return newStreamingResponseEntity(HttpStatus.OK, MediaType.IMAGE_PNG_VALUE, resource);
    }

    protected ResponseEntity<StreamingResponseBody> newStreamingResponseEntity(String contentType, Integer contentLength, @Nullable String filename, InputStream input) {
        StreamingResponseBody responseBody = new StreamingResponseBody() {
            @Override
            public void writeTo(OutputStream out) throws IOException {
                try (InputStream in = input) {
                    IOUtils.copy(in, out);
                }
            }
        };
        var headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        if (contentType != null) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        } else {
            headers.setContentType(MediaType.IMAGE_JPEG);
        }
        String safeFilename = sanitizeFilename(filename);
        if (StringUtils.isNotBlank(safeFilename)) {
            ContentDisposition cd = ContentDisposition
                    .inline() // attachment()로 바꾸면 다운로드 강제
                    .filename(safeFilename, StandardCharsets.UTF_8) // RFC 5987: filename*
                    .build();
            headers.setContentDisposition(cd);
        }
        if (contentLength != null && contentLength > 0) {
            headers.setContentLength(contentLength);
        }
        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    private ResponseEntity<StreamingResponseBody> newStreamingResponseEntity(HttpStatus status, String contentType, Resource resource) throws IOException {
        if (resource.exists()) {
            StreamingResponseBody responseBody = new StreamingResponseBody() {
                @Override
                public void writeTo(OutputStream out) throws IOException {
                    try (InputStream in = resource.getInputStream()) {
                        IOUtils.copy(in, out);
                    }
                }
            };
            var headers = new HttpHeaders();
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
            if (contentType != null) {
                headers.setContentType(MediaType.parseMediaType(contentType));
            } else {
                headers.setContentType(MediaType.IMAGE_JPEG);
            }
            long length = resource.contentLength();
            if (length > 0) {
                headers.setContentLength(length);
            }
            String safeFilename = sanitizeFilename(resource.getFilename());
            if (StringUtils.isNotBlank(safeFilename)) {
                ContentDisposition cd = ContentDisposition
                        .inline() // attachment()로 바꾸면 다운로드 강제
                        .filename(safeFilename, StandardCharsets.UTF_8) // RFC 5987: filename*
                        .build();
                headers.setContentDisposition(cd);
            }
            return new ResponseEntity<>(responseBody, headers, status);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    protected String sanitizeFilename(@Nullable String filename) {
        return Optional.ofNullable(filename)
                .map(org.springframework.util.StringUtils::getFilename)
                .map(name -> name.replace("\\", "").replace("/", ""))
                .filter(StringUtils::isNotBlank)
                .orElse(null);
    }
}

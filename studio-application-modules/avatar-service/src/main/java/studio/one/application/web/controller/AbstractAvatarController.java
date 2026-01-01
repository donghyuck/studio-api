package studio.one.application.web.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.web.dto.AvatarImageDto;
import studio.one.base.security.userdetails.ApplicationUserDetails;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.User;
import studio.one.platform.mediaio.ImageSources;
import studio.one.platform.mediaio.util.ImageResize;
import studio.one.platform.mediaio.util.MediaTypeUtil;
import studio.one.platform.web.dto.ApiResponse;

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

    protected Long getPrincipalUserId(UserDetails principal) {
        if (principal instanceof ApplicationUserDetails<?> aud) {
            return aud.getUserId();
        }
        return null;
    }

    protected ImageResize.Fit parseFit(String raw) {
        if (!org.springframework.util.StringUtils.hasText(raw)) {
            return ImageResize.Fit.CONTAIN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ImageResize.Fit.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ImageResize.Fit.CONTAIN;
        }
    }

    protected String contentTypeFromFormat(String format, String fallback) {
        if (format == null) {
            return fallback;
        }
        String f = format.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> fallback;
        };
    }

    protected ResponseEntity<ApiResponse<AvatarImageDto>> transformImage(
            AvatarImageService<User> avatarImageService,
            Long userId,
            Long avatarImageId,
            Integer width,
            Integer height,
            ImageResize.Fit fit) throws IOException {
        if (userId == null || userId <= 0 || avatarImageId == null || avatarImageId <= 0) {
            return badRequest("Invalid identifier");
        }
        if (width == null || height == null || width <= 0 || height <= 0) {
            return badRequest("Invalid size");
        }
        var imgOpt = avatarImageService.findById(avatarImageId);
        if (imgOpt.isEmpty()) {
            return badRequest("Avatar not found");
        }
        AvatarImage image = imgOpt.get();
        if (!image.getUserId().equals(userId)) {
            return forbidden("Forbidden");
        }
        var inOpt = avatarImageService.openDataStream(image);
        if (inOpt.isEmpty()) {
            return badRequest("Avatar data not available");
        }
        BufferedImage src;
        try (InputStream in = inOpt.get()) {
            src = javax.imageio.ImageIO.read(in);
        }
        if (src == null) {
            return badRequest("Invalid image data");
        }
        BufferedImage resized = ImageResize.resize(src, width, height, fit);
        String format = MediaTypeUtil.guessWriteFormat(image.getContentType(), image.getFileName());
        ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
        javax.imageio.ImageIO.write(resized, format, bos);
        byte[] bytes = bos.toByteArray();
        String contentType = contentTypeFromFormat(format, image.getContentType());
        try (var srcBytes = ImageSources.of(bytes, image.getFileName(), contentType)) {
            AvatarImage saved = avatarImageService.replaceData(image, srcBytes);
            return ResponseEntity.ok(ApiResponse.ok(AvatarImageDto.of(saved)));
        }
    }

    protected <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.<T>builder().message(message).build());
    }

    protected <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<T>builder().message(message).build());
    }
}

package studio.one.application.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.avatar.domain.entity.AvatarImage;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.application.web.dto.AvatarImageDto;
import studio.one.application.web.dto.AvatarImageMetaUpdateRequest;
import studio.one.application.web.dto.AvatarPresenceDto;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.mediaio.ImageSources;
import studio.one.platform.mediaio.util.ImageResize;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".avatar-image.web.self-base:/api/me/avatar}")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("isAuthenticated()")
public class MeAvatarController extends AbstractAvatarController {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB 상한

    private final AvatarImageService<User> avatarImageService;

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<AvatarImage>>> list(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return forbidden("Forbidden");
        }
        var list = avatarImageService.findAllByUser(toUser(userId));
        return ok(ApiResponse.ok(list));
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarImageDto>> uploadImage(
            @RequestParam(value = "primary", defaultValue = "true", required = false) Boolean primary,
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam MultipartFile file) throws IOException {

        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return forbidden("Forbidden");
        }
        if (file == null || file.isEmpty()) {
            return badRequest("File is empty");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            return badRequest("File too large");
        }
        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(sanitizedName)) {
            return badRequest("Invalid file name");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            return badRequest("Only image content types are allowed");
        }

        var meta = new AvatarImage();
        meta.setUserId(userId);
        meta.setFileName(sanitizedName);
        meta.setContentType(contentType);
        meta.setFileSize(file.getSize());
        meta.setPrimaryImage(primary);
        try (var src = ImageSources.of(file)) {
            var saved = avatarImageService.upload(meta, src, toUser(userId));
            return ok(ApiResponse.ok(AvatarImageDto.of(saved)));
        }
    }

    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<AvatarPresenceDto>> avatarCount(
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return forbidden("Forbidden");
        }
        long count = avatarImageService.countByUser(toUser(userId));
        Optional<AvatarImage> primary = avatarImageService.findPrimaryByUser(toUser(userId));
        AvatarPresenceDto dto = new AvatarPresenceDto(
                count > 0,
                (int) count,
                primary.map(AvatarImage::getId).orElse(null),
                primary.map(AvatarImage::getModifiedDate).orElse(null));
        log.info("Primary Avatar Image Count: {}", count);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePrivate())
                .body(ApiResponse.ok(dto));
    }

    @GetMapping("/primary")
    public ResponseEntity<StreamingResponseBody> downloadPrimaryImage(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(value = "width", defaultValue = "0", required = false) Integer width,
            @RequestParam(value = "height", defaultValue = "0", required = false) Integer height) throws IOException {

        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return notAavaliable();
        }
        if (width != null && width < 0) {
            width = 0;
        }
        if (height != null && height < 0) {
            height = 0;
        }
        var primaryOpt = avatarImageService.findPrimaryByUser(toUser(userId));
        if (primaryOpt.isEmpty())
            return notAavaliable();

        var meta = primaryOpt.get();
        var inOpt = avatarImageService.openDataStream(meta);
        if (inOpt.isEmpty())
            return notAavaliable();
        return newStreamingResponseEntity(meta.getContentType(), meta.getFileSize().intValue(), meta.getFileName(),
                inOpt.get());
    }

    @PutMapping("/{avatarImageId:[\\p{Digit}]+}/primary")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable("avatarImageId") Long avatarImageId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0 || avatarImageId == null || avatarImageId <= 0) {
            return badRequest("Invalid identifier");
        }
        var imgOpt = avatarImageService.findById(avatarImageId);
        if (imgOpt.isPresent()) {
            AvatarImage image = imgOpt.get();
            if (image.getUserId().equals(userId)) {
                avatarImageService.setPrimary(image);
            }
        }
        return ok(ApiResponse.ok());
    }

    @DeleteMapping("/{avatarImageId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("avatarImageId") Long avatarImageId,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0 || avatarImageId == null || avatarImageId <= 0) {
            return badRequest("Invalid identifier");
        }
        var imgOpt = avatarImageService.findById(avatarImageId);
        if (imgOpt.isPresent()) {
            AvatarImage image = imgOpt.get();
            if (image.getUserId().equals(userId)) {
                avatarImageService.remove(image);
            }
        }
        return ok(ApiResponse.ok());
    }

    @PostMapping("/{avatarImageId:[\\p{Digit}]+}/resize")
    public ResponseEntity<ApiResponse<AvatarImageDto>> resize(
            @PathVariable("avatarImageId") Long avatarImageId,
            @RequestParam("width") Integer width,
            @RequestParam("height") Integer height,
            @RequestParam(value = "fit", defaultValue = "CONTAIN") String fit,
            @AuthenticationPrincipal UserDetails principal) throws IOException {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return forbidden("Forbidden");
        }
        return transformImage(avatarImageService, userId, avatarImageId, width, height, parseFit(fit));
    }

    @PostMapping("/{avatarImageId:[\\p{Digit}]+}/crop")
    public ResponseEntity<ApiResponse<AvatarImageDto>> crop(
            @PathVariable("avatarImageId") Long avatarImageId,
            @RequestParam("width") Integer width,
            @RequestParam("height") Integer height,
            @AuthenticationPrincipal UserDetails principal) throws IOException {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0) {
            return forbidden("Forbidden");
        }
        return transformImage(avatarImageService, userId, avatarImageId, width, height, ImageResize.Fit.COVER);
    }

    @PutMapping("/{avatarImageId:[\\p{Digit}]+}/meta")
    public ResponseEntity<ApiResponse<AvatarImageDto>> updateMeta(
            @PathVariable("avatarImageId") Long avatarImageId,
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody AvatarImageMetaUpdateRequest req) {
        Long userId = getPrincipalUserId(principal);
        if (userId == null || userId <= 0 || avatarImageId == null || avatarImageId <= 0) {
            return badRequest("Invalid identifier");
        }
        if (req == null) {
            return badRequest("Invalid request");
        }
        String sanitizedName = sanitizeFilename(req.fileName());
        if (req.fileName() != null && !StringUtils.hasText(sanitizedName)) {
            return badRequest("Invalid file name");
        }
        if (sanitizedName == null && req.primaryImage() == null) {
            return badRequest("Nothing to update");
        }
        var imgOpt = avatarImageService.findById(avatarImageId);
        if (imgOpt.isEmpty()) {
            return badRequest("Avatar not found");
        }
        AvatarImage image = imgOpt.get();
        if (!image.getUserId().equals(userId)) {
            return forbidden("Forbidden");
        }
        AvatarImage updated = avatarImageService.updateMetadata(image, sanitizedName, req.primaryImage());
        return ok(ApiResponse.ok(AvatarImageDto.of(updated)));
    }
}

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
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.mediaio.ImageSources;
import studio.one.platform.mediaio.util.ImageResize;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".avatar-image.web.mgmt-base:/api/mgmt/users}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AvatarController extends AbstractAvatarController {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB 상한

    private final AvatarImageService avatarImageService;

    /**
     * 사용자 아바타 메타 목록 조회
     * 
     * @param userId
     * @return
     */
    @GetMapping("/{userId:[\\p{Digit}]+}/avatars")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','read')")
    public ResponseEntity<ApiResponse<List<AvatarImage>>> list(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return badRequest("Invalid userId");
        }
        var list = avatarImageService.findAllByUserId(userId);
        return ok(ApiResponse.ok(list));
    }

    @PostMapping(value = "/{userId:[\\p{Digit}]+}/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<AvatarImageDto>> uploadImage(
            @PathVariable Long userId,
            @RequestParam(value = "primary", defaultValue = "true", required = false) Boolean primary,
            @RequestParam MultipartFile file) throws IOException {

        if (userId == null || userId <= 0) {
            return badRequest("Invalid userId");
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
            var saved = avatarImageService.upload(meta, src);
            return ok(ApiResponse.ok(AvatarImageDto.of(saved)));
        }
    }

    @GetMapping("/{userId:[\\p{Digit}]+}/avatars/exists")
    public ResponseEntity<ApiResponse<AvatarPresenceDto>> avatarCount(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return badRequest("Invalid userId");
        }
        long count = avatarImageService.countByUserId(userId);
        Optional<AvatarImage> primary = avatarImageService.findPrimaryByUserId(userId);
        AvatarPresenceDto dto = new AvatarPresenceDto(
                count > 0,
                (int)count,
                primary.map(AvatarImage::getId).orElse(null),
                primary.map(AvatarImage::getModifiedDate).orElse(null));
        log.info("Primary Avatar Image Count: {}", count);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.SECONDS).cachePrivate())
                .body( ApiResponse.ok(dto) );        
    }

    /**
     * 사용자 대표 아바타 이미지 다운로드
     */
    @GetMapping("/{userId:[\\p{Digit}]+}/avatars/primary")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','read')")
    public ResponseEntity<StreamingResponseBody> downloadPrimaryImage(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "width", defaultValue = "0", required = false) Integer width,
            @RequestParam(value = "height", defaultValue = "0", required = false) Integer height) throws IOException {

        if (userId == null || userId <= 0) {
            return notAavaliable();
        }
        if (width != null && width < 0) {
            width = 0;
        }
        if (height != null && height < 0) {
            height = 0;
        }
        var primaryOpt = avatarImageService.findPrimaryByUserId(userId);
        if (primaryOpt.isEmpty())
            return notAavaliable();

        var meta = primaryOpt.get();
        var inOpt = avatarImageService.openDataStream(meta);
        if (inOpt.isEmpty())
            return notAavaliable();
        return newStreamingResponseEntity(meta.getContentType(), meta.getFileSize().intValue(), meta.getFileName(),
                inOpt.get());
    }

    /**
     * 대표 아바타 이미지 설정
     * 
     * @param userId
     * @param avatarImageId
     * @return
     */
    @PutMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}/primary")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId) {
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

    /**
     * 아바타 이미지 삭제
     * 
     * @param userId
     * @param avatarImageId
     * @return
     */
    @DeleteMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId) {
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

    @PostMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}/resize")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<AvatarImageDto>> resize(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId,
            @RequestParam("width") Integer width,
            @RequestParam("height") Integer height,
            @RequestParam(value = "fit", defaultValue = "CONTAIN") String fit) throws IOException {
        return transformImage(avatarImageService, userId, avatarImageId, width, height, parseFit(fit));
    }

    @PostMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}/crop")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<AvatarImageDto>> crop(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId,
            @RequestParam("width") Integer width,
            @RequestParam("height") Integer height) throws IOException {
        return transformImage(avatarImageService, userId, avatarImageId, width, height, ImageResize.Fit.COVER);
    }

    @PutMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}/meta")
    @PreAuthorize("@endpointAuthz.can('features:avatar-image','write')")
    public ResponseEntity<ApiResponse<AvatarImageDto>> updateMeta(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId,
            @RequestBody AvatarImageMetaUpdateRequest req) {
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

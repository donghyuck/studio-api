package studio.one.application.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import studio.one.application.web.dto.AvatarPresenceDto;
import studio.one.base.user.domain.model.User;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.mediaio.ImageSources;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".avatar-image.web.user-base:/api/mgmt/users}")
@RequiredArgsConstructor
@Slf4j
public class AvatarController extends AbstractAvatarController {

    private final AvatarImageService<User> avatarImageService;

    /**
     * 사용자 아바타 메타 목록 조회
     * 
     * @param userId
     * @return
     */
    @GetMapping("/{userId:[\\p{Digit}]+}/avatars")
    public ResponseEntity<ApiResponse<List<AvatarImage>>> list(@PathVariable Long userId) {
        var list = avatarImageService.findAllByUser(toUser(userId));
        return ok(ApiResponse.ok(list));
    }

    @PostMapping(value = "/{userId:[\\p{Digit}]+}/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarImageDto>> uploadImage(
            @PathVariable Long userId,
            @RequestParam(value = "primary", defaultValue = "true", required = false) Boolean primary,
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam MultipartFile file) throws IOException {

        var meta = new AvatarImage();
        meta.setUserId(userId);
        meta.setFileName(file.getOriginalFilename());
        meta.setContentType(file.getContentType());
        meta.setFileSize(file.getSize());
        meta.setPrimaryImage(primary);
        try (var src = ImageSources.of(file)) {
            var saved = avatarImageService.upload(meta, src, toUser(userId));
            return ok(ApiResponse.ok(AvatarImageDto.of(saved)));
        }
    }

    @GetMapping("/{userId:[\\p{Digit}]+}/avatars/exists")
    public ResponseEntity<ApiResponse<AvatarPresenceDto>> avatarCount(@PathVariable Long userId) {
        long count = avatarImageService.countByUser(toUser(userId));
        avatarImageService.findPrimaryByUser(toUser(userId));
        Optional<AvatarImage> primary = avatarImageService.findPrimaryByUser(toUser(userId));
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
    public ResponseEntity<StreamingResponseBody> downloadPrimaryImage(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "width", defaultValue = "0", required = false) Integer width,
            @RequestParam(value = "height", defaultValue = "0", required = false) Integer height) throws IOException {

        if (userId <= 0) {
            return notAavaliable();
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

    /**
     * 대표 아바타 이미지 설정
     * 
     * @param userId
     * @param avatarImageId
     * @return
     */
    @PutMapping("/{userId:[\\p{Digit}]+}/avatars/{avatarImageId:[\\p{Digit}]+}/primary")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId) {
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
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("userId") Long userId,
            @PathVariable("avatarImageId") Long avatarImageId) {
        var imgOpt = avatarImageService.findById(avatarImageId);
        if (imgOpt.isPresent()) {
            AvatarImage image = imgOpt.get();
            if (image.getUserId().equals(userId)) {
                avatarImageService.remove(image);
            }
        }
        return ok(ApiResponse.ok());
    }

}

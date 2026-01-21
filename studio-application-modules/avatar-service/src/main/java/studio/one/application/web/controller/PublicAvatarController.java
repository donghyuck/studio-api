package studio.one.application.web.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.avatar.service.AvatarImageService;
import studio.one.platform.constant.PropertyKeys;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".avatar-image.web.public-base:/api/profile}")
@RequiredArgsConstructor
@Slf4j
public class PublicAvatarController extends AbstractAvatarController {
    
    private final AvatarImageService avatarImageService;

    @GetMapping("/{username}/avatar") 
    public ResponseEntity<StreamingResponseBody> downloadUserAvatarImage(
            @PathVariable("username") String username,
            @RequestParam(value = "width", defaultValue = "0", required = false) Integer width,
            @RequestParam(value = "height", defaultValue = "0", required = false) Integer height) throws IOException {
        
        if (username == null || username.isBlank()) {
            return notAavaliable();
        }
        if (width != null && width < 0) {
            width = 0;
        }
        if (height != null && height < 0) {
            height = 0;
        }
        var primaryOpt = avatarImageService.findPrimaryByUsername(username);        
        if (primaryOpt.isEmpty()) return notAavaliable();
        var meta = primaryOpt.get();
        var inOpt = avatarImageService.openDataStream(meta);
        if (inOpt.isEmpty()) return notAavaliable();
        return newStreamingResponseEntity(meta.getContentType(), meta.getFileSize().intValue(), meta.getFileName(), inOpt.get() );
    }

    @GetMapping(value = "/{userId:[\\p{Digit}]+}/avatar", params = "byId")
    public ResponseEntity<StreamingResponseBody> downloadUserAvatarImageByUserId(
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
        if (primaryOpt.isEmpty()) return notAavaliable();
        var meta = primaryOpt.get();
        var inOpt = avatarImageService.openDataStream(meta);
        if (inOpt.isEmpty()) return notAavaliable();
        return newStreamingResponseEntity(meta.getContentType(), meta.getFileSize().intValue(), meta.getFileName(), inOpt.get() );
    }

}

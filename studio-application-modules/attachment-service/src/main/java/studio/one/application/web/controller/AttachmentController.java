package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.dto.UserDto;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.base-path:/api/mgmt/attachments}")
@RequiredArgsConstructor
@Slf4j
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final ApplicationUserService<?, ?> userService;
    private final ApplicationUserMapper userMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:attachment','upload')")
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) throws IOException {

        Attachment saved = attachmentService.createAttachment(
                objectType,
                objectId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                (int) file.getSize());
        AttachmentDto dto = toDto(saved);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<AttachmentDto>> get(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        return ResponseEntity.ok(ApiResponse.ok(toDto(attachment)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/download")
    @PreAuthorize("@endpointAuthz.can('features:attachment','download')")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("attachmentId") long attachmentId)
            throws IOException, NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        InputStream in = attachmentService.getInputStream(attachment);
        StreamingResponseBody body = out -> {
            try (in) {
                IOUtils.copy(in, out);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setContentType(resolveMediaType(attachment.getContentType()));
        headers.setContentLength(attachment.getSize());
        if (StringUtils.hasText(attachment.getName())) {
            ContentDisposition cd = ContentDisposition.attachment()
                    .filename(attachment.getName())
                    .build();
            headers.setContentDisposition(cd);
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AttachmentDto>>> list(
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @PageableDefault Pageable pageable) {
        Page<Attachment> page;
        if (objectType != null && objectId != null) {
            page = attachmentService.findAttachemnts(objectType, objectId, pageable);
        } else {
            page = attachmentService.findAttachemnts(pageable);
        }
        Page<AttachmentDto> dtoPage = page.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/objects/{objectType:[\\p{Digit}]+}/{objectId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listByObject(
            @PathVariable int objectType,
            @PathVariable long objectId) {
        List<Attachment> attachments = attachmentService.getAttachments(objectType, objectId);
        List<AttachmentDto> dto = attachments.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException, IOException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        attachmentService.removeAttachment(attachment);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private AttachmentDto toDto(Attachment attachment) {
        UserDto creator = findUserDto(attachment.getCreatedBy(), attachment.getAttachmentId());
        return AttachmentDto.of(attachment, creator);
    }

    private UserDto findUserDto(long userId, long attachmentId) {
        if (userId <= 0) {
            return null;
        }
        try {
            User user = userService.get(userId);
            return userMapper.toDto(user);
        } catch (NotFoundException e) {
            log.warn("User {} not found for attachment {}", userId, attachmentId);
            return null;
        }
    }

    private MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

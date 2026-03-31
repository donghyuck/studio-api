package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserDto;
import studio.one.platform.identity.UserRef;
import studio.one.platform.text.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.self-base:/api/me/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("isAuthenticated()")
public class MeAttachmentController {

    private static final long MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024; // 50MB 상한으로 자원 고갈 방지

    private final AttachmentService attachmentService;
    private final ObjectProvider<IdentityService> identityServiceProvider;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws IOException {

        AttachmentWebSupport.requireUserId(userId);
        if (file == null || file.isEmpty()) {
            return AttachmentWebSupport.badRequest("File is empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            return AttachmentWebSupport.badRequest("File too large");
        }
        if (file.getSize() > Integer.MAX_VALUE) {
            return AttachmentWebSupport.badRequest("File size exceeds supported limit");
        }
        String sanitizedName = AttachmentWebSupport.sanitizeFilename(file.getOriginalFilename());
        if (sanitizedName == null) {
            return AttachmentWebSupport.badRequest("Invalid file name");
        }
        String contentType = AttachmentWebSupport.resolveMediaTypeString(file.getContentType());

        Attachment saved = attachmentService.createAttachment(
                objectType,
                objectId,
                sanitizedName,
                contentType,
                file.getInputStream(),
                (int) file.getSize());
        AttachmentDto dto = toDto(saved);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<AttachmentDto>> get(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(toDto(attachment)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/text")
    public ResponseEntity<ApiResponse<String>> extractText(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException, IOException {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (extractor == null) {
            ApiResponse<String> body = ApiResponse.<String>builder()
                    .message("Text extraction is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            return ResponseEntity.ok(ApiResponse.ok(text));
        }
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws IOException, NotFoundException {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        InputStream in = attachmentService.getInputStream(attachment);
        StreamingResponseBody body = out -> {
            try (in) {
                IOUtils.copy(in, out);
            }
        };
        var headers = AttachmentWebSupport.downloadHeaders(attachment.getContentType(), attachment.getSize(), attachment.getName());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AttachmentDto>>> list(
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PageableDefault Pageable pageable) {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        Page<Attachment> page;
        if (objectType != null && objectId != null) {
            if (keyword == null || keyword.isBlank()) {
                page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId, pageable);
            } else {
                page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId, keyword,
                        pageable);
            }
        } else {
            if (keyword == null || keyword.isBlank()) {
                page = attachmentService.findAttachmentsByCreator(resolvedUserId, pageable);
            } else {
                page = attachmentService.findAttachmentsByCreator(resolvedUserId, keyword, pageable);
            }
        }
        Page<AttachmentDto> dtoPage = page.map(this::toDto);
        return ResponseEntity.ok(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/objects/{objectType:[\\p{Digit}]+}/{objectId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listByObject(
            @PathVariable int objectType,
            @PathVariable long objectId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        List<Attachment> attachments = attachmentService.getAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId);
        List<AttachmentDto> dto = attachments.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/{attachmentId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException, IOException {
        long resolvedUserId = AttachmentWebSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
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
        IdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            return null;
        }
        return identityService.findById(userId)
                .map(this::toUserDto)
                .orElseGet(() -> {
                    log.warn("User {} not found for attachment {}", userId, attachmentId);
                    return null;
                });
    }

    private UserDto toUserDto(UserRef userRef) {
        return new UserDto(userRef.userId(), userRef.username());
    }

}

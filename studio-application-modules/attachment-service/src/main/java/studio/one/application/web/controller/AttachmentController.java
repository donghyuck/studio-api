package studio.one.application.web.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.base-path:/api/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AttachmentController {

    private static final long MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024; // 50MB 상한으로 자원 고갈 방지

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-upload')")
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return badRequest("File is empty");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            return badRequest("File too large");
        }
        if (file.getSize() > Integer.MAX_VALUE) {
            return badRequest("File size exceeds supported limit");
        }
        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(sanitizedName)) {
            return badRequest("Invalid file name");
        }
        String contentType = resolveMediaTypeString(file.getContentType());

        Attachment saved = attachmentService.createAttachment(
                objectType,
                objectId,
                sanitizedName,
                contentType,
                file.getInputStream(),
                (int) file.getSize());
        AttachmentDto dto = AttachmentDto.of(saved, null);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-read')")
    public ResponseEntity<ApiResponse<AttachmentDto>> get(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        return ResponseEntity.ok(ApiResponse.ok(AttachmentDto.of(attachment, null)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/download")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-download')")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("attachmentId") long attachmentId)
            throws IOException, NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        StreamingResponseBody body = out -> {
            try (var in = attachmentService.getInputStream(attachment)) {
                in.transferTo(out);
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

    @GetMapping("/objects/{objectType:[\\p{Digit}]+}/{objectId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-read')")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listByObject(
            @PathVariable int objectType,
            @PathVariable long objectId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault org.springframework.data.domain.Pageable pageable) {
        List<Attachment> attachments;
        if (keyword == null || keyword.isBlank()) {
            attachments = attachmentService.findAttachments(objectType, objectId, pageable).getContent();
        } else {
            attachments = attachmentService.findAttachments(objectType, objectId, keyword, pageable).getContent();
        }
        List<AttachmentDto> dto = attachments.stream()
                .map(a -> AttachmentDto.of(a, null))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException, IOException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        attachmentService.removeAttachment(attachment);
        return ResponseEntity.ok(ApiResponse.ok());
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

    private String resolveMediaTypeString(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(contentType).toString();
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private String sanitizeFilename(String original) {
        if (!StringUtils.hasText(original)) {
            return null;
        }
        return original.replace("\\", "/").replaceAll(".*/", "");
    }

    private <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        ApiResponse<T> body = ApiResponse.<T>builder()
                .message(message)
                .build();
        return ResponseEntity.badRequest().body(body);
    }
}

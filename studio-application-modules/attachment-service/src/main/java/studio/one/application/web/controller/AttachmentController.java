package studio.one.application.web.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.beans.factory.ObjectProvider;
import studio.one.application.attachment.thumbnail.ThumbnailData;
import studio.one.application.attachment.thumbnail.ThumbnailService;
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
    private final ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-upload')")
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file) throws IOException {

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
        var headers = AttachmentWebSupport.downloadHeaders(attachment.getContentType(), attachment.getSize(), attachment.getName());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/thumbnail")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-read')")
    public ResponseEntity<StreamingResponseBody> thumbnail(
            @PathVariable("attachmentId") long attachmentId,
            @RequestParam(value = "size", required = false, defaultValue = "128") int size,
            @RequestParam(value = "format", required = false, defaultValue = "png") String format)
            throws NotFoundException {
        ThumbnailService thumbnailService = thumbnailServiceProvider.getIfAvailable();
        if (thumbnailService == null) {
            return ResponseEntity.status(501).build();
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        var result = thumbnailService.getOrCreate(attachment, size, format);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        ThumbnailData data = result.get();
        StreamingResponseBody body = out -> {
            out.write(data.getBytes());
        };
        var headers = AttachmentWebSupport.thumbnailHeaders(data.getContentType(), data.getBytes().length);
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
}

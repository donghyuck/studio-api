package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.service.AttachmentDownloadAuditLogCommand;
import studio.one.application.attachment.service.AttachmentDownloadAuditLogService;
import studio.one.application.attachment.service.AttachmentDownloadAuditResult;
import studio.one.application.attachment.service.AttachmentDownloadTokenInspection;
import studio.one.application.attachment.service.AttachmentDownloadTokenInspectionStatus;
import studio.one.application.attachment.service.AttachmentDownloadUrl;
import studio.one.application.attachment.service.AttachmentDownloadTokenClaims;
import studio.one.application.attachment.service.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.service.AttachmentDownloadUrlService;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.attachment.thumbnail.ThumbnailData;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.application.web.dto.AttachmentDownloadUrlDto;
import studio.one.application.web.dto.AttachmentDownloadUrlIssueRequestDto;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.base-path:/api/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AttachmentController {

    private static final String LINK_TYPE_APPLICATION_SIGNED = "APPLICATION_SIGNED";

    private final AttachmentService attachmentService;
    private final AttachmentDownloadUrlService downloadUrlService;
    private final AttachmentDownloadAuditLogService downloadAuditLogService;
    private final AttachmentUrlIssueRequestDetailsResolver requestDetailsResolver;
    private final ObjectProvider<ThumbnailService> thumbnailServiceProvider;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-upload')")
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        AttachmentWebSupport.PreparedUpload upload;
        try {
            upload = AttachmentWebSupport.prepareUpload(file);
        } catch (IllegalArgumentException e) {
            return AttachmentWebSupport.badRequest(e.getMessage());
        }

        Attachment saved = attachmentService.createAttachment(
                objectType,
                objectId,
                upload.name(),
                upload.contentType(),
                file.getInputStream(),
                upload.sizeBytes());
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
        return AttachmentWebSupport.downloadResponse(
                attachment,
                attachmentService.getInputStream(attachment),
                CacheControl.noCache());
    }

    @PostMapping("/{attachmentId:[\\p{Digit}]+}/download-url")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-download')")
    public ResponseEntity<ApiResponse<AttachmentDownloadUrlDto>> issueDownloadUrl(
            @PathVariable("attachmentId") long attachmentId,
            @RequestBody(required = false) AttachmentDownloadUrlIssueRequestDto request,
            HttpServletRequest httpRequest) throws NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        AttachmentUrlIssueRequestDetails details = requestDetailsResolver.resolve(httpRequest);
        try {
            AttachmentDownloadUrl issued = downloadUrlService.issueDownloadUrl(
                    attachment,
                    request == null ? null : request.ttlSeconds(),
                    AttachmentDownloadUrlEndpointKind.SERVICE,
                    AttachmentWebSupport.auditActor(principalResolverProvider),
                    details.clientIp(),
                    details.userAgent());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.ok(new AttachmentDownloadUrlDto(issued.url(), issued.expiresAt())));
        } catch (IllegalArgumentException ex) {
            return AttachmentWebSupport.badRequest(ex.getMessage());
        } catch (AttachmentDownloadUrlUnavailableException ex) {
            return AttachmentWebSupport.conflict("Attachment download URL is not available");
        }
    }

    @GetMapping("/signed-download")
    public ResponseEntity<StreamingResponseBody> signedDownload(
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest httpRequest)
            throws IOException, NotFoundException {
        Instant requestedAt = Instant.now();
        AttachmentUrlIssueRequestDetails details = requestDetailsResolver.resolve(httpRequest);
        AttachmentDownloadTokenInspection inspection = downloadUrlService.inspectDownloadToken(token);
        if (inspection.status() == AttachmentDownloadTokenInspectionStatus.INVALID_TOKEN) {
            recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                    inspection.tokenHash(),
                    null,
                    null,
                    null,
                    LINK_TYPE_APPLICATION_SIGNED,
                    requestedAt,
                    AttachmentDownloadAuditResult.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED.value(),
                    null,
                    details.clientIp(),
                    details.userAgent(),
                    "TOKEN_INVALID"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        AttachmentDownloadTokenClaims claims = inspection.claims();
        if (inspection.status() == AttachmentDownloadTokenInspectionStatus.EXPIRED) {
            recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                    inspection.tokenHash(),
                    claims.attachmentId(),
                    null,
                    null,
                    LINK_TYPE_APPLICATION_SIGNED,
                    requestedAt,
                    AttachmentDownloadAuditResult.EXPIRED,
                    HttpStatus.UNAUTHORIZED.value(),
                    null,
                    details.clientIp(),
                    details.userAgent(),
                    "TOKEN_EXPIRED"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        Attachment attachment;
        try {
            attachment = attachmentService.getAttachmentById(claims.attachmentId());
        } catch (NotFoundException ex) {
            recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                    inspection.tokenHash(),
                    claims.attachmentId(),
                    null,
                    null,
                    LINK_TYPE_APPLICATION_SIGNED,
                    requestedAt,
                    AttachmentDownloadAuditResult.FAILED,
                    HttpStatus.NOT_FOUND.value(),
                    null,
                    details.clientIp(),
                    details.userAgent(),
                    "ATTACHMENT_NOT_FOUND"));
            throw ex;
        }

        InputStream input;
        try {
            input = attachmentService.getInputStream(attachment);
        } catch (IOException | RuntimeException ex) {
            recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                    inspection.tokenHash(),
                    attachment.getAttachmentId(),
                    attachment.getObjectType(),
                    attachment.getObjectId(),
                    LINK_TYPE_APPLICATION_SIGNED,
                    requestedAt,
                    AttachmentDownloadAuditResult.FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    null,
                    details.clientIp(),
                    details.userAgent(),
                    "STREAM_OPEN_FAILED"));
            throw ex;
        }

        return auditedSignedDownloadResponse(
                attachment,
                input,
                inspection.tokenHash(),
                requestedAt,
                details);
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/thumbnail")
    @PreAuthorize("@endpointAuthz.can('features:attachment','service-download')")
    public ResponseEntity<StreamingResponseBody> thumbnail(
            @PathVariable("attachmentId") long attachmentId,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "format", required = false) String format)
            throws NotFoundException {
        ThumbnailService thumbnailService = thumbnailServiceProvider.getIfAvailable();
        if (thumbnailService == null) {
            return ResponseEntity.status(501).build();
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        int requestedSize = size == null ? 0 : size;
        var result = thumbnailService.getOrCreate(attachment, requestedSize, format);
        if (result.isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Thumbnail-Status", "unavailable");
            headers.setCacheControl(CacheControl.noStore().getHeaderValue());
            return ResponseEntity.noContent()
                    .headers(headers)
                    .build();
        }
        ThumbnailData data = result.get();
        StreamingResponseBody body = out -> out.write(data.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Thumbnail-Status", data.getStatus());
        if (data.isPending()) {
            headers.setCacheControl(CacheControl.noStore().getHeaderValue());
            headers.add(HttpHeaders.RETRY_AFTER, "3");
        } else {
            headers.setCacheControl(CacheControl.maxAge(3600, java.util.concurrent.TimeUnit.SECONDS).getHeaderValue());
        }
        headers.setContentType(AttachmentWebSupport.resolveMediaType(data.getContentType()));
        headers.setContentLength(data.getBytes().length);
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

    private ResponseEntity<StreamingResponseBody> auditedSignedDownloadResponse(
            Attachment attachment,
            InputStream input,
            String tokenHash,
            Instant requestedAt,
            AttachmentUrlIssueRequestDetails details) {
        StreamingResponseBody body = out -> {
            long downloadedBytes = 0L;
            byte[] buffer = new byte[8192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloadedBytes += read;
                }
                recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                        tokenHash,
                        attachment.getAttachmentId(),
                        attachment.getObjectType(),
                        attachment.getObjectId(),
                        LINK_TYPE_APPLICATION_SIGNED,
                        requestedAt,
                        AttachmentDownloadAuditResult.SUCCEEDED,
                        HttpStatus.OK.value(),
                        downloadedBytes,
                        details.clientIp(),
                        details.userAgent(),
                        null));
            } catch (IOException | RuntimeException ex) {
                recordDownloadAudit(new AttachmentDownloadAuditLogCommand(
                        tokenHash,
                        attachment.getAttachmentId(),
                        attachment.getObjectType(),
                        attachment.getObjectId(),
                        LINK_TYPE_APPLICATION_SIGNED,
                        requestedAt,
                        AttachmentDownloadAuditResult.FAILED,
                        HttpStatus.OK.value(),
                        downloadedBytes,
                        details.clientIp(),
                        details.userAgent(),
                        "STREAM_FAILED"));
                throw ex;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore().getHeaderValue());
        headers.setContentType(AttachmentWebSupport.resolveMediaType(attachment.getContentType()));
        headers.setContentLength(attachment.getSize());
        if (StringUtils.hasText(attachment.getName())) {
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(attachment.getName())
                    .build());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    private void recordDownloadAudit(AttachmentDownloadAuditLogCommand command) {
        try {
            downloadAuditLogService.record(command);
        } catch (RuntimeException ex) {
            log.warn("Failed to record attachment signed download audit log: {}", ex.getMessage());
        }
    }
}

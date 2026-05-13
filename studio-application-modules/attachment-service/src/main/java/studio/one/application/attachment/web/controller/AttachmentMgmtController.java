package studio.one.application.attachment.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import studio.one.application.attachment.application.error.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.application.result.AttachmentDownloadUrl;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.result.ThumbnailData;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlService;
import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentObjectTypeResolver;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.application.usecase.ThumbnailService;
import studio.one.application.attachment.web.dto.response.AttachmentDownloadUrlDto;
import studio.one.application.attachment.web.dto.request.AttachmentDownloadUrlIssueRequestDto;
import studio.one.application.attachment.web.dto.response.AttachmentDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.mgmt-base-path:/api/mgmt/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AttachmentMgmtController {

    private final AttachmentService attachmentService;
    private final AttachmentDownloadUrlService downloadUrlService;
    private final AttachmentUrlIssueRequestDetailsResolver requestDetailsResolver;
    private final ObjectProvider<IdentityService> identityServiceProvider;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<ThumbnailService> thumbnailServiceProvider;
    private final ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;
    private final AttachmentObjectTypeResolver objectTypeResolver;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@endpointAuthz.can('features:attachment','upload')")
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
        ApplicationPrincipal principal = requirePrincipal();
        AttachmentAccessSupport.requireOwnerAccess(
                objectType,
                objectId,
                principal,
                ownerAccessAuthorizers,
                objectTypeResolver,
                AttachmentOwnerAccessAction.UPLOAD);

        Attachment saved = attachmentService.createAttachment(
                objectType,
                objectId,
                upload.name(),
                upload.contentType(),
                file.getInputStream(),
                upload.sizeBytes());
        return ResponseEntity.ok(ApiResponse.ok(toDto(saved)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<AttachmentDto>> get(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment, requirePrincipal(), ownerAccessAuthorizers, objectTypeResolver, AttachmentOwnerAccessAction.READ);
        return ResponseEntity.ok(ApiResponse.ok(toDto(attachment)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/text")
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<String>> extractText(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException, IOException {
        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (extractor == null) {
            ApiResponse<String> body = ApiResponse.<String>builder()
                    .message("Text extraction is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment, requirePrincipal(), ownerAccessAuthorizers, objectTypeResolver, AttachmentOwnerAccessAction.READ);
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            return ResponseEntity.ok(ApiResponse.ok(text));
        }
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/download")
    @PreAuthorize("@endpointAuthz.can('features:attachment','download')")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("attachmentId") long attachmentId)
            throws IOException, NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment, requirePrincipal(), ownerAccessAuthorizers, objectTypeResolver, AttachmentOwnerAccessAction.DOWNLOAD);
        return AttachmentWebSupport.downloadResponse(
                attachment,
                attachmentService.getInputStream(attachment),
                CacheControl.noCache());
    }

    @PostMapping("/{attachmentId:[\\p{Digit}]+}/download-url")
    @PreAuthorize("@endpointAuthz.can('features:attachment','download')")
    public ResponseEntity<ApiResponse<AttachmentDownloadUrlDto>> issueDownloadUrl(
            @PathVariable("attachmentId") long attachmentId,
            @RequestBody(required = false) AttachmentDownloadUrlIssueRequestDto request,
            HttpServletRequest httpRequest) throws NotFoundException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        ApplicationPrincipal principal = requirePrincipal();
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment, principal, ownerAccessAuthorizers, objectTypeResolver, AttachmentOwnerAccessAction.ISSUE_DOWNLOAD_URL);
        AttachmentUrlIssueRequestDetails details = requestDetailsResolver.resolve(httpRequest);
        try {
            AttachmentDownloadUrl issued = downloadUrlService.issueDownloadUrl(
                    attachment,
                    request == null ? null : request.ttlSeconds(),
                    AttachmentDownloadUrlEndpointKind.MGMT,
                    AttachmentWebSupport.auditActor(principal),
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

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<Page<AttachmentDto>>> list(
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault Pageable pageable) {
        ApplicationPrincipal principal = requirePrincipal();
        boolean admin = AttachmentAccessSupport.isAdmin(principal);
        Page<Attachment> page;
        if (objectType != null && objectId != null) {
            if (admin) {
                if (keyword == null || keyword.isBlank()) {
                    page = attachmentService.findAttachments(objectType, objectId, pageable);
                } else {
                    page = attachmentService.findAttachments(objectType, objectId, keyword, pageable);
                }
            } else if (AttachmentAccessSupport.isWellKnownDomainAttachmentType(objectType, objectTypeResolver)) {
                AttachmentAccessSupport.requireOwnerAccess(
                        objectType,
                        objectId,
                        principal,
                        ownerAccessAuthorizers,
                        objectTypeResolver,
                        AttachmentOwnerAccessAction.LIST);
                if (keyword == null || keyword.isBlank()) {
                    page = attachmentService.findAttachments(objectType, objectId, pageable);
                } else {
                    page = attachmentService.findAttachments(objectType, objectId, keyword, pageable);
                }
            } else {
                long userId = AttachmentAccessSupport.requireUserId(principal);
                if (keyword == null || keyword.isBlank()) {
                    page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, userId, pageable);
                } else {
                    page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, userId, keyword,
                            pageable);
                }
            }
        } else if (admin) {
            if (keyword == null || keyword.isBlank()) {
                page = attachmentService.findAttachments(pageable);
            } else {
                page = attachmentService.findAttachments(keyword, pageable);
            }
        } else {
            long userId = AttachmentAccessSupport.requireUserId(principal);
            if (keyword == null || keyword.isBlank()) {
                page = attachmentService.findAttachmentsByCreator(userId, pageable);
            } else {
                page = attachmentService.findAttachmentsByCreator(userId, keyword, pageable);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(page.map(this::toDto)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/thumbnail")
    @PreAuthorize("@endpointAuthz.can('features:attachment','download')")
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
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment,
                requirePrincipal(),
                ownerAccessAuthorizers,
                objectTypeResolver,
                AttachmentOwnerAccessAction.DOWNLOAD);
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
    @PreAuthorize("@endpointAuthz.can('features:attachment','read')")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listByObject(
            @PathVariable int objectType,
            @PathVariable long objectId) {
        ApplicationPrincipal principal = requirePrincipal();
        List<Attachment> attachments;
        if (AttachmentAccessSupport.isAdmin(principal)) {
            attachments = attachmentService.getAttachments(objectType, objectId);
        } else if (AttachmentAccessSupport.isWellKnownDomainAttachmentType(objectType, objectTypeResolver)) {
            AttachmentAccessSupport.requireOwnerAccess(
                    objectType,
                    objectId,
                    principal,
                    ownerAccessAuthorizers,
                    objectTypeResolver,
                    AttachmentOwnerAccessAction.LIST);
            attachments = attachmentService.getAttachments(objectType, objectId);
        } else {
            attachments = attachmentService.getAttachmentsByObjectAndCreator(
                    objectType,
                    objectId,
                    AttachmentAccessSupport.requireUserId(principal));
        }
        return ResponseEntity.ok(ApiResponse.ok(attachments.stream().map(this::toDto).collect(java.util.stream.Collectors.toList())));
    }

    @DeleteMapping("/{attachmentId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:attachment','delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException, IOException {
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment, requirePrincipal(), ownerAccessAuthorizers, objectTypeResolver, AttachmentOwnerAccessAction.DELETE);
        attachmentService.removeAttachment(attachment);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private ApplicationPrincipal requirePrincipal() {
        return AttachmentAccessSupport.requirePrincipal(principalResolverProvider);
    }

    private AttachmentDto toDto(Attachment attachment) {
        return AttachmentWebSupport.toDto(attachment, identityServiceProvider);
    }
}

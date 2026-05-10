package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
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
import studio.one.application.attachment.service.AttachmentOwnerAccessAction;
import studio.one.application.attachment.service.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.self-base:/api/me/attachments}")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("isAuthenticated()")
public class MeAttachmentController {

    private final AttachmentService attachmentService;
    private final ObjectProvider<IdentityService> identityServiceProvider;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AttachmentDto>> upload(
            @RequestParam("objectType") int objectType,
            @RequestParam("objectId") long objectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws IOException {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        AttachmentWebSupport.PreparedUpload upload;
        try {
            upload = AttachmentWebSupport.prepareUpload(file);
        } catch (IllegalArgumentException e) {
            return AttachmentWebSupport.badRequest(e.getMessage());
        }
        requireOwnerAccess(objectType, objectId, principal(resolvedUserId), AttachmentOwnerAccessAction.UPLOAD);

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
    public ResponseEntity<ApiResponse<AttachmentDto>> get(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requireAttachmentAccess(attachment, principal(resolvedUserId), AttachmentOwnerAccessAction.READ);
        return ResponseEntity.ok(ApiResponse.ok(toDto(attachment)));
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/text")
    public ResponseEntity<ApiResponse<String>> extractText(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException, IOException {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
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
        requireAttachmentAccess(attachment, principal(resolvedUserId), AttachmentOwnerAccessAction.READ);
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            return ResponseEntity.ok(ApiResponse.ok(text));
        }
    }

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws IOException, NotFoundException {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requireAttachmentAccess(attachment, principal(resolvedUserId), AttachmentOwnerAccessAction.DOWNLOAD);
        return AttachmentWebSupport.downloadResponse(
                attachment,
                attachmentService.getInputStream(attachment),
                CacheControl.noCache());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AttachmentDto>>> list(
            @RequestParam(value = "objectType", required = false) Integer objectType,
            @RequestParam(value = "objectId", required = false) Long objectId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PageableDefault Pageable pageable) {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        Page<Attachment> page;
        if (objectType != null && objectId != null) {
            requireOwnerAccess(objectType, objectId, principal(resolvedUserId), AttachmentOwnerAccessAction.LIST);
            if (keyword == null || keyword.isBlank()) {
                page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId, pageable);
            } else {
                page = attachmentService.findAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId, keyword,
                        pageable);
            }
        } else if (keyword == null || keyword.isBlank()) {
            page = attachmentService.findAttachmentsByCreator(resolvedUserId, pageable);
        } else {
            page = attachmentService.findAttachmentsByCreator(resolvedUserId, keyword, pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(page.map(this::toDto)));
    }

    @GetMapping("/objects/{objectType:[\\p{Digit}]+}/{objectId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listByObject(
            @PathVariable int objectType,
            @PathVariable long objectId,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        requireOwnerAccess(objectType, objectId, principal(resolvedUserId), AttachmentOwnerAccessAction.LIST);
        List<Attachment> attachments = attachmentService.getAttachmentsByObjectAndCreator(objectType, objectId, resolvedUserId);
        return ResponseEntity.ok(ApiResponse.ok(attachments.stream().map(this::toDto).toList()));
    }

    @DeleteMapping("/{attachmentId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("attachmentId") long attachmentId,
            @AuthenticationPrincipal(expression = "userId") Long userId) throws NotFoundException, IOException {
        long resolvedUserId = AttachmentAccessSupport.requireUserId(userId);
        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        if (attachment.getCreatedBy() != resolvedUserId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        requireAttachmentAccess(attachment, principal(resolvedUserId), AttachmentOwnerAccessAction.DELETE);
        attachmentService.removeAttachment(attachment);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private void requireAttachmentAccess(
            Attachment attachment,
            ApplicationPrincipal principal,
            AttachmentOwnerAccessAction action) {
        if (!AttachmentAccessSupport.isWellKnownDomainAttachmentType(attachment.getObjectType())) {
            return;
        }
        AttachmentAccessSupport.requireAttachmentAccess(
                attachment,
                principal,
                ownerAccessAuthorizers,
                action);
    }

    private void requireOwnerAccess(
            int objectType,
            long objectId,
            ApplicationPrincipal principal,
            AttachmentOwnerAccessAction action) {
        if (!AttachmentAccessSupport.isWellKnownDomainAttachmentType(objectType)) {
            return;
        }
        AttachmentAccessSupport.requireOwnerAccess(
                objectType,
                objectId,
                principal,
                ownerAccessAuthorizers,
                action);
    }

    private ApplicationPrincipal principal(long userId) {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        if (resolver != null) {
            ApplicationPrincipal principal = resolver.currentOrNull();
            if (principal != null) {
                return principal;
            }
        }
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return "user-" + userId;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("USER");
            }
        };
    }

    private AttachmentDto toDto(Attachment attachment) {
        return AttachmentWebSupport.toDto(attachment, identityServiceProvider);
    }
}

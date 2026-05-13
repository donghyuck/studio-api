package studio.one.application.template.web.controller;

import java.io.IOException;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.application.usecase.TemplatesService;
import studio.one.application.template.web.dto.response.TemplateDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".template.web.base-path:/api/mgmt/templates}")
@RequiredArgsConstructor
@Validated
public class TemplateMgmtController {

    private final TemplatesService templatesService;
    private final ObjectProvider<IdentityService> identityServiceProvider;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:template','write')")
    public ResponseEntity<ApiResponse<TemplateDto>> create(
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal UserDetails principal) throws IOException {
        long userId = requireUserId(principal);
        Template created = templatesService.createGenericTemplates(
                request.objectType(),
                request.objectId(),
                request.name(),
                request.displayName(),
                request.description(),
                request.subject(),
                request.bodyInputStream());
        created.setCreatedBy(userId);
        created.setUpdatedBy(userId);
        if (request.properties() != null) {
            created.setProperties(request.properties());
        }
        templatesService.saveOrUpdate(created);
        return ResponseEntity.ok(ApiResponse.ok(toDto(created)));
    }

    @GetMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<TemplateDto>> get(@PathVariable long templateId) throws NotFoundException {
        Template template = resolveTemplate(templateId);
        return ResponseEntity.ok(ApiResponse.ok(toDto(template)));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<Page<TemplateDto>>> list(
            @PageableDefault(size = 20, sort = "templateId", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "fields", required = false) String fields) {
        validateFields(fields);
        ApplicationPrincipal principal = requirePrincipal();
        Page<TemplateDto> page = (isAdmin(principal)
                ? templatesService.page(pageable, query, fields)
                : templatesService.pageByCreatedBy(requireUserId(principal), pageable, query, fields))
                .map(this::toSummaryDto);
        return ResponseEntity.ok()
                .header("X-Template-Search-Fields", SearchFields.allowedCsv())
                .body(ApiResponse.ok(page));
    }

    @GetMapping("/name/{name}")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<TemplateDto>> getByName(@PathVariable String name) throws NotFoundException {
        ApplicationPrincipal principal = requirePrincipal();
        Template template = isAdmin(principal)
                ? templatesService.getTemplatesByName(name)
                : templatesService.getTemplatesByNameAndCreator(name, requireUserId(principal));
        return ResponseEntity.ok(ApiResponse.ok(toDto(template)));
    }

    @PutMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','write')")
    public ResponseEntity<ApiResponse<TemplateDto>> update(
            @PathVariable long templateId,
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal UserDetails principal) throws NotFoundException {
        long userId = requireUserId(principal);
        Template existing = resolveTemplate(templateId);
        existing.setObjectType(request.objectType());
        existing.setObjectId(request.objectId());
        existing.setName(request.name());
        existing.setDisplayName(request.displayName());
        existing.setDescription(request.description());
        existing.setSubject(request.subject());
        existing.setBody(request.body());
        existing.setProperties(request.properties());
        existing.setUpdatedBy(userId);
        templatesService.saveOrUpdate(existing);
        return ResponseEntity.ok(ApiResponse.ok(toDto(existing)));
    }

    @DeleteMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','delete')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable long templateId,
            @AuthenticationPrincipal UserDetails principal) throws NotFoundException, IOException {
        long userId = requireUserId(principal);
        Template template = resolveTemplate(templateId);
        template.setUpdatedBy(userId);
        templatesService.saveOrUpdate(template);
        templatesService.remove(template);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{templateId:[\\p{Digit}]+}/render/body")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<String>> renderBody(
            @PathVariable long templateId,
            @RequestBody(required = false) Map<String, Object> model) throws Exception {
        Template template = resolveTemplate(templateId);
        String rendered = templatesService.processBody(template, model == null ? Map.of() : model);
        return ResponseEntity.ok(ApiResponse.ok(rendered));
    }

    @PostMapping("/{templateId:[\\p{Digit}]+}/render/subject")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<String>> renderSubject(
            @PathVariable long templateId,
            @RequestBody(required = false) Map<String, Object> model) throws Exception {
        Template template = resolveTemplate(templateId);
        String rendered = templatesService.processSubject(template, model == null ? Map.of() : model);
        return ResponseEntity.ok(ApiResponse.ok(rendered));
    }

    public static class TemplateRequest {
        @NotNull
        private Integer objectType;
        @NotNull
        private Long objectId;
        @NotBlank
        private String name;
        private String displayName;
        private String description;
        private String subject;
        private String body;
        private Map<String, String> properties;

        public TemplateRequest() {
        }

        public TemplateRequest(
                @NotNull Integer objectType,
                @NotNull Long objectId,
                @NotBlank String name,
                String displayName,
                String description,
                String subject,
                String body,
                Map<String, String> properties) {
            this.objectType = objectType;
            this.objectId = objectId;
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.subject = subject;
            this.body = body;
            this.properties = properties;
        }

        public Integer getObjectType() { return objectType; }

        public void setObjectType(Integer objectType) { this.objectType = objectType; }

        public Integer objectType() { return objectType; }

        public Long getObjectId() { return objectId; }

        public void setObjectId(Long objectId) { this.objectId = objectId; }

        public Long objectId() { return objectId; }

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String name() { return name; }

        public String getDisplayName() { return displayName; }

        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }

        public String getDescription() { return description; }

        public void setDescription(String description) { this.description = description; }

        public String description() { return description; }

        public String getSubject() { return subject; }

        public void setSubject(String subject) { this.subject = subject; }

        public String subject() { return subject; }

        public String getBody() { return body; }

        public void setBody(String body) { this.body = body; }

        public String body() { return body; }

        public Map<String, String> getProperties() { return properties; }

        public void setProperties(Map<String, String> properties) { this.properties = properties; }

        public Map<String, String> properties() { return properties; }

        java.io.InputStream bodyInputStream() {
            if (body == null) {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
            return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private TemplateDto toDto(Template template) {
        return TemplateDto.from(template, TemplateWebSupport.findUserDto(identityServiceProvider, template.getCreatedBy()),
                TemplateWebSupport.findUserDto(identityServiceProvider, template.getUpdatedBy()));
    }

    private TemplateDto toSummaryDto(Template template) {
        return TemplateDto.summary(
                template,
                TemplateWebSupport.findUserDto(identityServiceProvider, template.getCreatedBy()),
                TemplateWebSupport.findUserDto(identityServiceProvider, template.getUpdatedBy()));
    }

    private void validateFields(String fields) {
        if (fields == null || fields.isBlank()) {
            return;
        }
        for (String raw : fields.split(",")) {
            String field = raw.trim();
            if (!field.isEmpty() && !SearchFields.ALLOWED.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported fields value. Allowed: " + SearchFields.allowedCsv());
            }
        }
    }

    private long requireUserId(UserDetails principal) {
        if (principal instanceof ApplicationPrincipal) {
            ApplicationPrincipal aud = (ApplicationPrincipal) principal;
            Long userId = aud.getUserId();
            if (userId != null && userId > 0) {
                return userId;
            }
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    private ApplicationPrincipal requirePrincipal() {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new AuthenticationCredentialsNotFoundException("No principal resolver configured");
        }
        ApplicationPrincipal principal = resolver.currentOrNull();
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return principal;
    }

    private boolean isAdmin(ApplicationPrincipal principal) {
        return principal != null && principal.hasRole("ADMIN");
    }

    private long requireUserId(ApplicationPrincipal principal) {
        if (principal != null && principal.getUserId() != null && principal.getUserId() > 0) {
            return principal.getUserId();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    private Template resolveTemplate(long templateId) throws NotFoundException {
        ApplicationPrincipal principal = requirePrincipal();
        return isAdmin(principal)
                ? templatesService.getTemplates(templateId)
                : templatesService.getTemplates(templateId, requireUserId(principal));
    }

    private static final class SearchFields {
        private static final java.util.List<String> ALLOWED = java.util.List.of(
                "name", "displayName", "description", "subject", "body");

        private static String allowedCsv() {
            return String.join(",", ALLOWED);
        }
    }
}

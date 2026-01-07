package studio.one.application.template.web.controller;

import java.io.IOException;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.service.TemplatesService;
import studio.one.application.template.web.dto.TemplateDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".template.web.base-path:/api/mgmt/templates}")
@RequiredArgsConstructor
@Validated
public class TemplateController {

    private final TemplatesService templatesService;

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:template','write')")
    public ResponseEntity<ApiResponse<TemplateDto>> create(@Valid @RequestBody TemplateRequest request)
            throws IOException {
        Template created = templatesService.createGenericTemplates(
                request.objectType(),
                request.objectId(),
                request.name(),
                request.displayName(),
                request.description(),
                request.subject(),
                request.bodyInputStream());
        if (request.properties() != null) {
            created.setProperties(request.properties());
            templatesService.saveOrUpdate(created);
        }
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(created)));
    }

    @GetMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<TemplateDto>> get(@PathVariable long templateId) throws NotFoundException {
        Template template = templatesService.getTemplates(templateId);
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(template)));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<Page<TemplateDto>>> list(
            @PageableDefault(size = 20, sort = "templateId", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "fields", required = false) String fields) {
        validateFields(fields);
        Page<TemplateDto> page = templatesService.page(pageable, query, fields).map(TemplateDto::summary);
        return ResponseEntity.ok()
                .header("X-Template-Search-Fields", SearchFields.allowedCsv())
                .body(ApiResponse.ok(page));
    }

    @GetMapping("/name/{name}")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<TemplateDto>> getByName(@PathVariable String name) throws NotFoundException {
        Template template = templatesService.getTemplatesByName(name);
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(template)));
    }

    @PutMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','write')")
    public ResponseEntity<ApiResponse<TemplateDto>> update(
            @PathVariable long templateId,
            @Valid @RequestBody TemplateRequest request) throws NotFoundException {
        Template existing = templatesService.getTemplates(templateId);
        existing.setObjectType(request.objectType());
        existing.setObjectId(request.objectId());
        existing.setName(request.name());
        existing.setDisplayName(request.displayName());
        existing.setDescription(request.description());
        existing.setSubject(request.subject());
        existing.setBody(request.body());
        existing.setProperties(request.properties());
        templatesService.saveOrUpdate(existing);
        return ResponseEntity.ok(ApiResponse.ok(TemplateDto.from(existing)));
    }

    @DeleteMapping("/{templateId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:template','delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long templateId)
            throws NotFoundException, IOException {
        Template template = templatesService.getTemplates(templateId);
        templatesService.remove(template);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{templateId:[\\p{Digit}]+}/render/body")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<String>> renderBody(
            @PathVariable long templateId,
            @RequestBody(required = false) Map<String, Object> model) throws Exception {
        Template template = templatesService.getTemplates(templateId);
        String rendered = templatesService.processBody(template, model == null ? Map.of() : model);
        return ResponseEntity.ok(ApiResponse.ok(rendered));
    }

    @PostMapping("/{templateId:[\\p{Digit}]+}/render/subject")
    @PreAuthorize("@endpointAuthz.can('features:template','read')")
    public ResponseEntity<ApiResponse<String>> renderSubject(
            @PathVariable long templateId,
            @RequestBody(required = false) Map<String, Object> model) throws Exception {
        Template template = templatesService.getTemplates(templateId);
        String rendered = templatesService.processSubject(template, model == null ? Map.of() : model);
        return ResponseEntity.ok(ApiResponse.ok(rendered));
    }

    /**
     * Incoming request payload for template create/update.
     */
    public record TemplateRequest(
            @NotNull Integer objectType,
            @NotNull Long objectId,
            @NotBlank String name,
            String displayName,
            String description,
            String subject,
            String body,
            Map<String, String> properties) {

        java.io.InputStream bodyInputStream() {
            if (body == null) {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
            return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
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

    private static final class SearchFields {
        private static final java.util.List<String> ALLOWED = java.util.List.of(
                "name", "displayName", "description", "subject", "body");

        private static String allowedCsv() {
            return String.join(",", ALLOWED);
        }
    }
}

package studio.one.platform.objecttype.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.objecttype.lifecycle.ObjectRebindService;
import studio.one.platform.objecttype.application.usecase.ObjectTypeAdminService;
import studio.one.platform.objecttype.application.command.ObjectTypePatchCommand;
import studio.one.platform.objecttype.application.command.ObjectTypePolicyUpsertCommand;
import studio.one.platform.objecttype.application.command.ObjectTypeUpsertCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.web.dto.response.ObjectTypeDto;
import studio.one.platform.objecttype.web.dto.response.ObjectTypeEffectivePolicyDto;
import studio.one.platform.objecttype.web.dto.request.ObjectTypePatchRequest;
import studio.one.platform.objecttype.web.dto.response.ObjectTypePolicyDto;
import studio.one.platform.objecttype.web.dto.request.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.request.ObjectTypeUpsertRequest;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.objecttype.web.mgmt-base-path:/api/mgmt/object-types}")
@RequiredArgsConstructor
@Validated
public class ObjectTypeMgmtController {

    private final ObjectTypeAdminService adminService;
    private final ObjectRebindService rebindService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<java.util.List<ObjectTypeDto>>> list(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q) {
        java.util.List<ObjectTypeDto> list = adminService.search(domain, status, q).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{objectType}")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> get(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.get(objectType))));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> create(@Valid @RequestBody ObjectTypeUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.upsert(toCommand(request)))));
    }

    @PutMapping("/{objectType}")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> upsert(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypeUpsertRequest request) {
        ObjectTypeUpsertRequest merged = request.objectType() != null
                ? request
                : new ObjectTypeUpsertRequest(objectType, request.code(), request.name(), request.domain(),
                        request.status(), request.description(), request.updatedBy(), request.updatedById(),
                        request.createdBy(), request.createdById());
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.upsert(toCommand(merged)))));
    }

    @PatchMapping("/{objectType}")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> patch(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypePatchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.patch(objectType, toCommand(request)))));
    }

    @GetMapping("/{objectType}/policy")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ObjectTypePolicyDto>> getPolicy(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.getPolicy(objectType))));
    }

    @GetMapping("/{objectType}/policy/effective")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ObjectTypeEffectivePolicyDto>> getEffectivePolicy(
            @PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.getEffectivePolicy(objectType))));
    }

    @PutMapping("/{objectType}/policy")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<ObjectTypePolicyDto>> upsertPolicy(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypePolicyUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(adminService.upsertPolicy(objectType, toCommand(request)))));
    }

    @DeleteMapping("/{objectType}")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @Min(1) int objectType) {
        adminService.delete(objectType);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reload")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','manage')")
    public ResponseEntity<ApiResponse<Void>> reload() {
        rebindService.rebind();
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private ObjectTypeDto toDto(ObjectTypeView view) {
        return ObjectTypeDto.builder()
                .objectType(view.objectType())
                .code(view.code())
                .name(view.name())
                .domain(view.domain())
                .status(view.status())
                .description(view.description())
                .createdBy(view.createdBy())
                .createdById(view.createdById())
                .createdAt(view.createdAt())
                .updatedBy(view.updatedBy())
                .updatedById(view.updatedById())
                .updatedAt(view.updatedAt())
                .build();
    }

    private ObjectTypePolicyDto toDto(studio.one.platform.objecttype.application.result.ObjectTypePolicyView view) {
        if (view == null) {
            return null;
        }
        return ObjectTypePolicyDto.builder()
                .objectType(view.objectType())
                .maxFileMb(view.maxFileMb())
                .allowedExt(view.allowedExt())
                .allowedMime(view.allowedMime())
                .policyJson(view.policyJson())
                .createdBy(view.createdBy())
                .createdById(view.createdById())
                .createdAt(view.createdAt())
                .updatedBy(view.updatedBy())
                .updatedById(view.updatedById())
                .updatedAt(view.updatedAt())
                .build();
    }

    private ObjectTypeEffectivePolicyDto toDto(
            studio.one.platform.objecttype.application.result.ObjectTypeEffectivePolicyView view) {
        return ObjectTypeEffectivePolicyDto.builder()
                .objectType(view.objectType())
                .maxFileMb(view.maxFileMb())
                .allowedExt(view.allowedExt())
                .allowedMime(view.allowedMime())
                .policyJson(view.policyJson())
                .source(view.source().value())
                .build();
    }

    private ObjectTypeUpsertCommand toCommand(ObjectTypeUpsertRequest request) {
        AuditActor actor = auditActor();
        return new ObjectTypeUpsertCommand(
                request.objectType(),
                request.code(),
                request.name(),
                request.domain(),
                request.status(),
                request.description(),
                actor.name(),
                actor.userId(),
                actor.name(),
                actor.userId());
    }

    private ObjectTypePatchCommand toCommand(ObjectTypePatchRequest request) {
        AuditActor actor = auditActor();
        return new ObjectTypePatchCommand(
                request.code(),
                request.name(),
                request.domain(),
                request.status(),
                request.description(),
                actor.name(),
                actor.userId());
    }

    private ObjectTypePolicyUpsertCommand toCommand(ObjectTypePolicyUpsertRequest request) {
        AuditActor actor = auditActor();
        return new ObjectTypePolicyUpsertCommand(
                request.maxFileMb(),
                request.allowedExt(),
                request.allowedMime(),
                request.policyJson(),
                actor.name(),
                actor.userId(),
                actor.name(),
                actor.userId());
    }

    private AuditActor auditActor() {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        ApplicationPrincipal principal = resolver == null ? null : resolver.currentOrNull();
        if (principal == null) {
            return new AuditActor("system", 0L);
        }
        return new AuditActor(
                principal.username().filter(name -> !name.isBlank()).orElse("system"),
                principal.userId().orElse(0L));
    }

    private record AuditActor(String name, Long userId) {
    }
}

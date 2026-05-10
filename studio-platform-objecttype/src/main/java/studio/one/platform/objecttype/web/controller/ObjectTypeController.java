package studio.one.platform.objecttype.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ObjectTypeView;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;
import studio.one.platform.objecttype.web.dto.response.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.request.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.response.ValidateUploadResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.objecttype.web.base-path:/api/object-types}")
@RequiredArgsConstructor
@Validated
public class ObjectTypeController {

    private final ObjectTypeRuntimeService runtimeService;

    @GetMapping("/{objectType}/definition")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ObjectTypeDefinitionDto>> definition(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(runtimeService.definition(objectType))));
    }

    @PostMapping("/{objectType}/validate-upload")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ValidateUploadResponse>> validateUpload(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ValidateUploadRequest request) {
        ValidateUploadResult result = runtimeService.validateUpload(objectType, toCommand(request));
        return ResponseEntity.ok(ApiResponse.ok(toDto(result)));
    }

    static ObjectTypeDefinitionDto toDto(ObjectTypeDefinition definition) {
        return ObjectTypeDefinitionDto.builder()
                .type(toDto(definition.type()))
                .policy(toDto(definition.policy()))
                .build();
    }

    private static studio.one.platform.objecttype.web.dto.response.ObjectTypeDto toDto(ObjectTypeView view) {
        return studio.one.platform.objecttype.web.dto.response.ObjectTypeDto.builder()
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

    private static studio.one.platform.objecttype.web.dto.response.ObjectTypePolicyDto toDto(
            studio.one.platform.objecttype.application.result.ObjectTypePolicyView view) {
        if (view == null) {
            return null;
        }
        return studio.one.platform.objecttype.web.dto.response.ObjectTypePolicyDto.builder()
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

    private ValidateUploadCommand toCommand(ValidateUploadRequest request) {
        return new ValidateUploadCommand(request.fileName(), request.contentType(), request.sizeBytes());
    }

    static ValidateUploadResponse toDto(ValidateUploadResult result) {
        return ValidateUploadResponse.builder()
                .allowed(result.allowed())
                .reason(result.reason())
                .build();
    }
}

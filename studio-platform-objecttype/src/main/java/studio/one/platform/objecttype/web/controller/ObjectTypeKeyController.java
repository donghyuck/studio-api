package studio.one.platform.objecttype.web.controller;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.web.dto.request.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.response.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.response.ValidateUploadResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.objecttype.web.base-path:/api/object-types}")
@RequiredArgsConstructor
@Validated
public class ObjectTypeKeyController {

    private final ObjectTypeRuntimeService runtimeService;

    @GetMapping("/keys/{key}/definition")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ObjectTypeDefinitionDto>> definitionByKey(@PathVariable String key) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(ObjectTypeController.toDto(runtimeService.definitionByKey(key))));
        } catch (UnsupportedOperationException ex) {
            return unsupportedKeyLookup();
        }
    }

    @PostMapping("/keys/{key}/validate-upload")
    @PreAuthorize("@endpointAuthz.can('features:objecttype','read')")
    public ResponseEntity<ApiResponse<ValidateUploadResponse>> validateUploadByKey(
            @PathVariable String key,
            @Valid @RequestBody ValidateUploadRequest request) {
        try {
            ValidateUploadResult result = runtimeService.validateUploadByKey(key, toCommand(request));
            return ResponseEntity.ok(ApiResponse.ok(ObjectTypeController.toDto(result)));
        } catch (UnsupportedOperationException ex) {
            ApiResponse<ValidateUploadResponse> body = ApiResponse.<ValidateUploadResponse>builder()
                    .message("ObjectType key lookup is not supported")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }
    }

    private ValidateUploadCommand toCommand(ValidateUploadRequest request) {
        return new ValidateUploadCommand(request.fileName(), request.contentType(), request.sizeBytes());
    }

    private ResponseEntity<ApiResponse<ObjectTypeDefinitionDto>> unsupportedKeyLookup() {
        ApiResponse<ObjectTypeDefinitionDto> body = ApiResponse.<ObjectTypeDefinitionDto>builder()
                .message("ObjectType key lookup is not supported")
                .build();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}

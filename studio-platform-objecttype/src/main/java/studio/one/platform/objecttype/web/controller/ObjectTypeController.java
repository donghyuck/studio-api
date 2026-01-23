package studio.one.platform.objecttype.web.controller;

import javax.validation.Valid;
import javax.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.objecttype.service.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.web.dto.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.ValidateUploadResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.objecttype.web.base-path:/api/object-types}")
@RequiredArgsConstructor
@Validated
public class ObjectTypeController {

    private final ObjectTypeRuntimeService runtimeService;

    @GetMapping("/{objectType}/definition")
    public ResponseEntity<ApiResponse<ObjectTypeDefinitionDto>> definition(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(runtimeService.definition(objectType)));
    }

    @PostMapping("/{objectType}/validate-upload")
    public ResponseEntity<ApiResponse<ValidateUploadResponse>> validateUpload(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ValidateUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(runtimeService.validateUpload(objectType, request)));
    }
}

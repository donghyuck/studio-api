package studio.one.platform.objecttype.web.controller;

import javax.validation.Valid;
import javax.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
import studio.one.platform.objecttype.service.ObjectTypeAdminService;
import studio.one.platform.objecttype.web.dto.ObjectTypeDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePatchRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypeUpsertRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.objecttype.web.mgmt-base-path:/api/mgmt/object-types}")
@RequiredArgsConstructor
@Validated
public class ObjectTypeMgmtController {

    private final ObjectTypeAdminService adminService;
    private final ObjectRebindService rebindService;

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ObjectTypeDto>>> list(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q) {
        java.util.List<ObjectTypeDto> list = adminService.search(domain, status, q);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{objectType}")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> get(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.get(objectType)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ObjectTypeDto>> create(@Valid @RequestBody ObjectTypeUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.upsert(request)));
    }

    @PutMapping("/{objectType}")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> upsert(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypeUpsertRequest request) {
        ObjectTypeUpsertRequest merged = request.objectType() != null
                ? request
                : new ObjectTypeUpsertRequest(objectType, request.code(), request.name(), request.domain(),
                        request.status(), request.description(), request.updatedBy(), request.updatedById(),
                        request.createdBy(), request.createdById());
        return ResponseEntity.ok(ApiResponse.ok(adminService.upsert(merged)));
    }

    @PatchMapping("/{objectType}")
    public ResponseEntity<ApiResponse<ObjectTypeDto>> patch(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypePatchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.patch(objectType, request)));
    }

    @GetMapping("/{objectType}/policy")
    public ResponseEntity<ApiResponse<ObjectTypePolicyDto>> getPolicy(@PathVariable @Min(1) int objectType) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getPolicy(objectType)));
    }

    @PutMapping("/{objectType}/policy")
    public ResponseEntity<ApiResponse<ObjectTypePolicyDto>> upsertPolicy(
            @PathVariable @Min(1) int objectType,
            @Valid @RequestBody ObjectTypePolicyUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.upsertPolicy(objectType, request)));
    }

    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Void>> reload() {
        rebindService.rebind();
        return ResponseEntity.ok(ApiResponse.ok());
    }
}

package studio.one.base.security.acl.web.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.acl.policy.AclPolicyDescriptor;
import studio.one.base.security.acl.policy.AclPolicySynchronizationService;
import studio.one.platform.web.dto.ApiResponse;

/**
 * REST controller that allows manual synchronization of ACL descriptors when
 * the web endpoint is enabled.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.security.acl.web.base-path:/api/mgmt/acl}/sync")
public class AclSyncController {

    private final List<AclPolicyDescriptor> policies;

    private final ObjectProvider<AclPolicySynchronizationService> synchronizationService;

    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/defaults")
    public ResponseEntity<ApiResponse<List<AclPolicyDescriptor>>> defaults() {
        return ResponseEntity.ok(ApiResponse.ok(Collections.unmodifiableList(policies)));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/sync")
    public ResponseEntity<Void> sync(@RequestBody AclPolicyDescriptor descriptor) {
        synchronizationService.ifAvailable(svc -> svc.synchronize(descriptor));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/sync/defaults")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> syncDefaults() {
        synchronizationService.ifAvailable(svc -> svc.synchronizeAll(policies));
        return ResponseEntity.noContent().build();
    }
}

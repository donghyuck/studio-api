package studio.one.base.security.acl.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.base.security.acl.policy.AclAction;
import studio.one.base.security.acl.web.dto.AclActionMaskDto;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Provides metadata about available ACL actions so administrators can select
 * corresponding masks when creating ACL entries.
 */
@RestController
@RequestMapping("${studio.security.acl.web.base-path:/api/mgmt/acl}/admin")
public class AclActionController {

    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<AclActionMaskDto>>> actions() {
        List<AclActionMaskDto> actions = Arrays.stream(AclAction.values())
                .map(action -> new AclActionMaskDto(action.name(), action.getMask()))
                .toList();
        return ok(ApiResponse.ok(actions));
    }
}





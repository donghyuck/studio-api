package studio.one.base.security.acl.web.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class AclObjectIdentityRequest {
    @NotNull
    private Long classId;

    @NotBlank
    private String objectIdentity;

    private Long parentId;
    private Long ownerSidId;
    private boolean entriesInheriting = true;
}

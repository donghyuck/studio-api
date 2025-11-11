package studio.one.base.security.acl.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AclObjectIdentityDto {
    private Long id;
    private Long classId;
    private String className;
    private String objectIdentity;
    private Long parentId;
    private Long ownerSidId;
    private boolean entriesInheriting;
}

package studio.one.base.security.acl.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclObjectIdentityResult {
    Long id;
    Long classId;
    String className;
    String objectIdentity;
    Long parentId;
    Long ownerSidId;
    boolean entriesInheriting;
}

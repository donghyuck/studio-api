package studio.one.base.security.acl.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclObjectIdentityCommand {
    Long classId;
    String objectIdentity;
    Long parentId;
    Long ownerSidId;
    boolean entriesInheriting;
}

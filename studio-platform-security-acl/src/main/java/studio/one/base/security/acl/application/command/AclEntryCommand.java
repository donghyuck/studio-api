package studio.one.base.security.acl.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclEntryCommand {
    Long objectIdentityId;
    Long sidId;
    Integer mask;
    Integer aceOrder;
    boolean granting;
    boolean auditSuccess;
    boolean auditFailure;
}

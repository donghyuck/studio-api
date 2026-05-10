package studio.one.base.security.acl.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclEntryResult {
    Long id;
    Long objectIdentityId;
    String objectIdentity;
    Long sidId;
    String sid;
    Integer aceOrder;
    Integer mask;
    boolean granting;
    boolean auditSuccess;
    boolean auditFailure;
}

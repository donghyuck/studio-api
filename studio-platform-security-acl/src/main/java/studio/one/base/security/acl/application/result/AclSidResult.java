package studio.one.base.security.acl.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclSidResult {
    Long id;
    boolean principal;
    String sid;
}

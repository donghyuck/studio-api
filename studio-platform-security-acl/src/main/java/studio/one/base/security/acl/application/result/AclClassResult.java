package studio.one.base.security.acl.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclClassResult {
    Long id;
    String className;
}

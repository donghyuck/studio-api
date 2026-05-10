package studio.one.base.security.acl.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AclClassCommand {
    String className;
}

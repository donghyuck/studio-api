package studio.echo.platform.security.authz;

import java.util.Map;

public interface DomainPolicyContributor {

  Map<String, AclProperties.DomainPolicy> contribute();

}

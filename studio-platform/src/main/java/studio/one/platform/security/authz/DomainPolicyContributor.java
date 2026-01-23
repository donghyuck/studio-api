package studio.one.platform.security.authz;

import java.util.Map;

import studio.one.platform.security.acl.AclProperties;

/**
 * An interface for components that contribute domain policies to the ACL.
 *
 * @author donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 */
public interface DomainPolicyContributor {

  /**
   * Contributes a map of domain policies.
   *
   * @return a map of domain names to their policies
   */
  Map<String, AclProperties.DomainPolicy> contribute();

}

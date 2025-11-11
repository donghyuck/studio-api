package studio.one.base.security.acl.policy;

import lombok.Value;

/**
 * Value object describing the resolved domain/component pair for an ACL entry.
 */
@Value
public class AclResourceLocation {
    String domain;
    String component;
}

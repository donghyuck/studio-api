package studio.one.base.security.acl.persistence;

/**
 * Projection for fetching ACL policy rows used to build domain policies.
 */
public interface AclPolicyProjection {

    String getClassName();

    String getObjectIdentity();

    String getSid();

    boolean isPrincipal();

    int getMask();

    boolean isGranting();
}

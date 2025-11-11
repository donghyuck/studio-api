package studio.one.base.security.acl.policy;

/**
 * Strategy for translating raw ACL class/object identifiers into
 * domain/component pairs understood by endpoint authorization policies.
 */
public interface AclResourceMapper {

    AclResourceLocation map(String className, String identity);
}

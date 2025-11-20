package studio.one.base.security.acl.policy;

import org.springframework.security.acls.domain.BasePermission;

/**
 * Represents normalized actions understood by endpoint/domain policies.
 */
public enum AclAction {
    READ(BasePermission.READ.getMask()),
    WRITE(BasePermission.WRITE.getMask()),
    CREATE(BasePermission.CREATE.getMask()),
    DELETE(BasePermission.DELETE.getMask()),
    ADMIN(BasePermission.ADMINISTRATION.getMask()),
    DOWNLOAD(1 << 5), // /READ
    UPLOAD(1 << 6); // READ | WRITE

    private final int mask;

    AclAction(int mask) {
        this.mask = mask;
    }

    /**
     * Returns the Spring Security ACL mask that corresponds to this action.
     */
    public int getMask() {
        return mask;
    }
}

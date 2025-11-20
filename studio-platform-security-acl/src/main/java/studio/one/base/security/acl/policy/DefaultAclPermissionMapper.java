package studio.one.base.security.acl.policy;

import java.util.EnumSet;

import org.springframework.security.acls.domain.BasePermission;

/**
 * Default implementation that maps Spring Security ACL base permissions to
 * read/write/admin actions.
 * 
 * 새로운 권한이 필요한 경우 DefaultAclPermissionMapper 와 AclAction 을 수정한다.
 */
public class DefaultAclPermissionMapper implements AclPermissionMapper {

    private static final int DOWNLOAD_MASK = 1 << 5;
    private static final int UPLOAD_MASK = 1 << 6;

    @Override
    public EnumSet<AclAction> map(int mask) {
        EnumSet<AclAction> actions = EnumSet.noneOf(AclAction.class);
        if ((mask & BasePermission.ADMINISTRATION.getMask()) != 0) {
            actions.add(AclAction.ADMIN);
        }
        if ((mask & BasePermission.WRITE.getMask()) != 0) {
            actions.add(AclAction.WRITE);
        }
        if ((mask & BasePermission.CREATE.getMask()) != 0) {
            actions.add(AclAction.CREATE);
        }
        if ((mask & BasePermission.DELETE.getMask()) != 0) {
            actions.add(AclAction.DELETE);
        }
        if ((mask & BasePermission.READ.getMask()) != 0) {
            actions.add(AclAction.READ);
        }
        if ((mask & DOWNLOAD_MASK) != 0) {
            actions.add(AclAction.DOWNLOAD);
            actions.add(AclAction.READ); // download implies read
        }
        if ((mask & UPLOAD_MASK) != 0) {
            actions.add(AclAction.UPLOAD);
            actions.add(AclAction.WRITE); // upload implies write
        }
        // 2. 파생 규칙
        // ADMIN 은 모든 액션을 포함한다고 가정
        if (actions.contains(AclAction.ADMIN)) {
            actions.add(AclAction.WRITE);
            actions.add(AclAction.READ);
            actions.add(AclAction.CREATE);
            actions.add(AclAction.DELETE);
            actions.add(AclAction.DOWNLOAD);
            actions.add(AclAction.UPLOAD);
        }
        // WRITE 권한이 있으면 최소 READ 도 있다고 보는 규칙을 적용하고 싶다면:
        if (actions.contains(AclAction.WRITE)) {
            actions.add(AclAction.READ);
        }
        return actions;
    }
}

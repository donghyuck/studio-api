package studio.one.base.user.domain.model.company;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import studio.one.base.user.domain.model.company.CompanyRole;

public final class CompanyPermissionActions {

    public static final String READ = "company.read";
    public static final String UPDATE = "company.update";
    public static final String ARCHIVE = "company.archive";
    public static final String MEMBER_READ = "company.member.read";
    public static final String MEMBER_MANAGE = "company.member.manage";
    public static final String PERMISSION_READ = "company.permission.read";
    public static final String PERMISSION_MANAGE = "company.permission.manage";
    public static final String WORKSPACE_CREATE = "company.workspace.create";
    public static final String WORKSPACE_READ = "company.workspace.read";
    public static final String BILLING_READ = "company.billing.read";
    public static final String BILLING_MANAGE = "company.billing.manage";

    private CompanyPermissionActions() {
    }

    public static Set<String> actionsFor(CompanyRole role) {
        if (role == null) {
            return Set.of();
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        actions.add(READ);
        actions.add(WORKSPACE_READ);
        switch (role) {
            case BILLING_ADMIN:
                actions.add(BILLING_READ);
                actions.add(BILLING_MANAGE);
                break;
            case ADMIN:
                addAdminActions(actions);
                break;
            case OWNER:
                addAdminActions(actions);
                actions.add(ARCHIVE);
                actions.add(PERMISSION_MANAGE);
                actions.add(BILLING_READ);
                actions.add(BILLING_MANAGE);
                break;
            case MEMBER:
                // base actions only
                break;
            default:
                break;
        }
        return Set.copyOf(actions);
    }

    private static void addAdminActions(Set<String> actions) {
        actions.add(UPDATE);
        actions.add(MEMBER_READ);
        actions.add(MEMBER_MANAGE);
        actions.add(PERMISSION_READ);
        actions.add(WORKSPACE_CREATE);
    }

    public static List<String> definitions() {
        return List.of(
                READ,
                UPDATE,
                ARCHIVE,
                MEMBER_READ,
                MEMBER_MANAGE,
                PERMISSION_READ,
                PERMISSION_MANAGE,
                WORKSPACE_CREATE,
                WORKSPACE_READ,
                BILLING_READ,
                BILLING_MANAGE);
    }
}

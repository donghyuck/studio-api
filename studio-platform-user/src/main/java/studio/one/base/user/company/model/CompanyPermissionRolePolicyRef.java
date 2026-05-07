package studio.one.base.user.company.model;

import java.util.List;

public record CompanyPermissionRolePolicyRef(
        CompanyRole role,
        List<String> actions,
        List<String> defaultActions,
        boolean override) {
}

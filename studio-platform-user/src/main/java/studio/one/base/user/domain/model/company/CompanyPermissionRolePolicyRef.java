package studio.one.base.user.domain.model.company;

import java.util.List;

public record CompanyPermissionRolePolicyRef(
        CompanyRole role,
        List<String> actions,
        List<String> defaultActions,
        boolean override) {
}

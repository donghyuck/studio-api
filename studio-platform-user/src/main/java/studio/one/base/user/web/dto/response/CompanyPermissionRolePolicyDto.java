package studio.one.base.user.web.dto.response;

import java.util.List;

import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyPermissionRolePolicyDto {
    private final CompanyRole role;
    private final List<String> actions;
    private final List<String> defaultActions;
    private final boolean override;

    public CompanyPermissionRolePolicyDto(CompanyRole role, List<String> actions, List<String> defaultActions, boolean override) {
        this.role = role;
        this.actions = actions;
        this.defaultActions = defaultActions;
        this.override = override;
    }

    public CompanyRole role() {
        return role;
    }

    public List<String> actions() {
        return actions;
    }

    public List<String> defaultActions() {
        return defaultActions;
    }

    public boolean override() {
        return override;
    }
}

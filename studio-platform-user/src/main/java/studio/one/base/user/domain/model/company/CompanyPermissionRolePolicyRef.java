package studio.one.base.user.domain.model.company;

import java.util.List;

public class CompanyPermissionRolePolicyRef {
    private final CompanyRole role;
    private final List<String> actions;
    private final List<String> defaultActions;
    private final boolean override;

    public CompanyPermissionRolePolicyRef(CompanyRole role, List<String> actions, List<String> defaultActions, boolean override) {
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

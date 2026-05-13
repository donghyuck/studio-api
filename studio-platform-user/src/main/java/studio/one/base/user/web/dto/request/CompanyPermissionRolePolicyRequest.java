package studio.one.base.user.web.dto.request;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyPermissionRolePolicyRequest {
    @NotNull
        private final CompanyRole role;
    @NotNull
        private final List<@NotBlank String> actions;
    @NotNull
        private final Boolean override;

    public CompanyPermissionRolePolicyRequest(@NotNull
        CompanyRole role, @NotNull
        List<@NotBlank String> actions, @NotNull
        Boolean override) {
        this.role = role;
        this.actions = actions;
        this.override = override;
    }

    public CompanyRole role() {
        return role;
    }

    public List<String> actions() {
        return actions;
    }

    public Boolean override() {
        return override;
    }
}

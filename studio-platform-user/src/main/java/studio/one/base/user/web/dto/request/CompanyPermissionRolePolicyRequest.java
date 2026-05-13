package studio.one.base.user.web.dto.request;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyPermissionRolePolicyRequest {
    @NotNull
        private CompanyRole role;
    @NotNull
        private List<@NotBlank String> actions;
    @NotNull
        private Boolean override;

    public CompanyPermissionRolePolicyRequest() {
    }

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

    public CompanyRole getRole() {
        return role;
    }

    public List<String> getActions() {
        return actions;
    }

    public Boolean getOverride() {
        return override;
    }

    public void setRole(CompanyRole role) {
        this.role = role;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public void setOverride(Boolean override) {
        this.override = override;
    }
}

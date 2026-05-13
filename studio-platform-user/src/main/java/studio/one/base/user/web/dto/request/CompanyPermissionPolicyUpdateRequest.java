package studio.one.base.user.web.dto.request;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CompanyPermissionPolicyUpdateRequest {
    @NotNull
        @Valid
        private List<CompanyPermissionRolePolicyRequest> roles;

    public CompanyPermissionPolicyUpdateRequest() {
    }

    public CompanyPermissionPolicyUpdateRequest(@NotNull
        @Valid
        List<CompanyPermissionRolePolicyRequest> roles) {
        this.roles = roles;
    }

    public List<CompanyPermissionRolePolicyRequest> roles() {
        return roles;
    }

    public List<CompanyPermissionRolePolicyRequest> getRoles() {
        return roles;
    }

    public void setRoles(List<CompanyPermissionRolePolicyRequest> roles) {
        this.roles = roles;
    }
}

package studio.one.base.user.web.dto.request;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class CompanyPermissionPolicyUpdateRequest {
    @NotNull
        @Valid
        private final List<CompanyPermissionRolePolicyRequest> roles;

    public CompanyPermissionPolicyUpdateRequest(@NotNull
        @Valid
        List<CompanyPermissionRolePolicyRequest> roles) {
        this.roles = roles;
    }

    public List<CompanyPermissionRolePolicyRequest> roles() {
        return roles;
    }
}

package studio.one.base.user.web.dto.request;

import javax.validation.constraints.NotNull;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyMemberRoleRequest {
    @NotNull private final CompanyRole role;

    public CompanyMemberRoleRequest(@NotNull CompanyRole role) {
        this.role = role;
    }

    public CompanyRole role() {
        return role;
    }
}

package studio.one.base.user.domain.model.company;

import java.util.List;

public class CompanyPermissionPolicyRef {
    private final Long companyId;
    private final List<CompanyPermissionRolePolicyRef> roles;

    public CompanyPermissionPolicyRef(Long companyId, List<CompanyPermissionRolePolicyRef> roles) {
        this.companyId = companyId;
        this.roles = roles;
    }

    public Long companyId() {
        return companyId;
    }

    public List<CompanyPermissionRolePolicyRef> roles() {
        return roles;
    }
}

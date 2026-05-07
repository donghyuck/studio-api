package studio.one.base.user.company.model;

import java.util.List;

public record CompanyPermissionPolicyRef(
        Long companyId,
        List<CompanyPermissionRolePolicyRef> roles) {
}

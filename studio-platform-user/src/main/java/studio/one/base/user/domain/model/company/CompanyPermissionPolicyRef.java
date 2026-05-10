package studio.one.base.user.domain.model.company;

import java.util.List;

public record CompanyPermissionPolicyRef(
        Long companyId,
        List<CompanyPermissionRolePolicyRef> roles) {
}

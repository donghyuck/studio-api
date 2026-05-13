package studio.one.base.user.web.dto.response;

import java.util.List;

public class CompanyPermissionPolicyDto {
    private final Long companyId;
    private final List<CompanyPermissionRolePolicyDto> roles;

    public CompanyPermissionPolicyDto(Long companyId, List<CompanyPermissionRolePolicyDto> roles) {
        this.companyId = companyId;
        this.roles = roles;
    }

    public Long companyId() {
        return companyId;
    }

    public List<CompanyPermissionRolePolicyDto> roles() {
        return roles;
    }
}

package studio.one.base.user.web.dto;

import java.util.List;

public record CompanyPermissionPolicyDto(
        Long companyId,
        List<CompanyPermissionRolePolicyDto> roles) {
}

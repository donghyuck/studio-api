package studio.one.base.user.web.dto.response;

import java.util.List;

public record CompanyPermissionPolicyDto(
        Long companyId,
        List<CompanyPermissionRolePolicyDto> roles) {
}

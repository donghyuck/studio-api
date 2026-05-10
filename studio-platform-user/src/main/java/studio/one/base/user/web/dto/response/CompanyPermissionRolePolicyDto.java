package studio.one.base.user.web.dto.response;

import java.util.List;

import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyPermissionRolePolicyDto(
        CompanyRole role,
        List<String> actions,
        List<String> defaultActions,
        boolean override) {
}

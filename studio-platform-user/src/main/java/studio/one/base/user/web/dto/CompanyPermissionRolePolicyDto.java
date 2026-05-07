package studio.one.base.user.web.dto;

import java.util.List;

import studio.one.base.user.company.model.CompanyRole;

public record CompanyPermissionRolePolicyDto(
        CompanyRole role,
        List<String> actions,
        List<String> defaultActions,
        boolean override) {
}

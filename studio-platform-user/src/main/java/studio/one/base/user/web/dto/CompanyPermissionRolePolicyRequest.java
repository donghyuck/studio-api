package studio.one.base.user.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyPermissionRolePolicyRequest(
        @NotNull
        CompanyRole role,
        List<String> actions) {
}

package studio.one.base.user.web.dto;

import jakarta.validation.constraints.NotNull;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyMemberRoleRequest(
        @NotNull CompanyRole role) {
}

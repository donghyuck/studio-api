package studio.one.base.user.web.dto.request;

import jakarta.validation.constraints.NotNull;
import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyMemberRoleRequest(
        @NotNull CompanyRole role) {
}

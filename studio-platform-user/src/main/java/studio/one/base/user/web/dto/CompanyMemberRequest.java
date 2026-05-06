package studio.one.base.user.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import studio.one.base.user.company.model.CompanyRole;

public record CompanyMemberRequest(
        @NotNull @Positive Long userId,
        @NotNull CompanyRole role) {
}

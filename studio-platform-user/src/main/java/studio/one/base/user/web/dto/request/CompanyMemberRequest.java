package studio.one.base.user.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyMemberRequest(
        @NotNull @Positive Long userId,
        @NotNull CompanyRole role) {
}

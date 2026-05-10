package studio.one.base.user.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import studio.one.base.user.domain.model.company.CompanyRole;

public record CompanyPermissionRolePolicyRequest(
        @NotNull
        CompanyRole role,
        @NotNull
        List<@NotBlank String> actions,
        @NotNull
        Boolean override) {
}

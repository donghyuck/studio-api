package studio.one.base.user.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CompanyPermissionPolicyUpdateRequest(
        @NotNull
        @Valid
        List<CompanyPermissionRolePolicyRequest> roles) {
}

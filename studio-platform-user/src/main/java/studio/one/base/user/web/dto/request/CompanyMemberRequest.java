package studio.one.base.user.web.dto.request;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import studio.one.base.user.domain.model.company.CompanyRole;

public class CompanyMemberRequest {
    @NotNull @Positive private final Long userId;
    @NotNull private final CompanyRole role;

    public CompanyMemberRequest(@NotNull @Positive Long userId, @NotNull CompanyRole role) {
        this.userId = userId;
        this.role = role;
    }

    public Long userId() {
        return userId;
    }

    public CompanyRole role() {
        return role;
    }
}

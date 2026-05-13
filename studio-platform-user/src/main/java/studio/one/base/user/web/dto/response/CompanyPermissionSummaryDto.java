package studio.one.base.user.web.dto.response;

import java.util.List;

public class CompanyPermissionSummaryDto {
    private final Long companyId;
    private final Long userId;
    private final List<String> actions;

    public CompanyPermissionSummaryDto(Long companyId, Long userId, List<String> actions) {
        this.companyId = companyId;
        this.userId = userId;
        this.actions = actions;
    }

    public Long companyId() {
        return companyId;
    }

    public Long userId() {
        return userId;
    }

    public List<String> actions() {
        return actions;
    }
}

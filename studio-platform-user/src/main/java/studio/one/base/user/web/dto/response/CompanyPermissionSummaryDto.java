package studio.one.base.user.web.dto.response;

import java.util.List;

public record CompanyPermissionSummaryDto(
        Long companyId,
        Long userId,
        List<String> actions) {
}

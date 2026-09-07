package studio.one.platform.team.web.dto.request;

import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamVisibility;

public record TeamUpdateRequest(
        Long companyId,
        Boolean clearCompanyAssignment,
        String name,
        String description,
        TeamVisibility visibility,
        TeamJoinPolicy joinPolicy,
        Boolean ragEnabled,
        TeamRagReplyMode ragReplyMode) {

    public boolean companyIdSpecified() {
        return companyId != null || Boolean.TRUE.equals(clearCompanyAssignment);
    }
}

package studio.one.platform.team.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamVisibility;

public record TeamCreateRequest(
        Long companyId,
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        TeamVisibility visibility,
        TeamJoinPolicy joinPolicy,
        Boolean ragEnabled,
        TeamRagReplyMode ragReplyMode,
        Boolean provisionRootWorkspace) {
}

package studio.one.platform.workspace.web.dto;

import jakarta.validation.constraints.Positive;

public record WorkspaceParentChangeRequest(
        @Positive Long newParentId) {
}

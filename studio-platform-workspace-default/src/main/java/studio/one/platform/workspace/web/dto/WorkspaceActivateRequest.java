package studio.one.platform.workspace.web.dto;

public record WorkspaceActivateRequest(Boolean cascade) {

    public boolean cascadeEnabled() {
        return Boolean.TRUE.equals(cascade);
    }
}

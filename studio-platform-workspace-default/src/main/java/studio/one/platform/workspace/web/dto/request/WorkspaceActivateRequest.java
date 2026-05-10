package studio.one.platform.workspace.web.dto.request;

public record WorkspaceActivateRequest(Boolean cascade) {

    public boolean cascadeEnabled() {
        return Boolean.TRUE.equals(cascade);
    }
}

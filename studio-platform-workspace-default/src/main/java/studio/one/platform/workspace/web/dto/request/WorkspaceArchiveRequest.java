package studio.one.platform.workspace.web.dto.request;

public record WorkspaceArchiveRequest(Boolean cascade) {

    public boolean cascadeEnabled() {
        return Boolean.TRUE.equals(cascade);
    }
}

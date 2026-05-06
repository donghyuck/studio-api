package studio.one.platform.workspace.web.dto;

public record WorkspaceArchiveRequest(Boolean cascade) {

    public boolean cascadeEnabled() {
        return Boolean.TRUE.equals(cascade);
    }
}

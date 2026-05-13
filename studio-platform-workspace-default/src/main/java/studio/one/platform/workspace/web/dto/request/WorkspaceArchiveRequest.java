package studio.one.platform.workspace.web.dto.request;

public class WorkspaceArchiveRequest {

    private Boolean cascade;

    public WorkspaceArchiveRequest() {
    }

    public WorkspaceArchiveRequest(
            Boolean cascade) {
        this.cascade = cascade;
    }

    public Boolean getCascade() { return cascade; }

    public void setCascade(Boolean cascade) { this.cascade = cascade; }

    public Boolean cascade() { return cascade; }

public boolean cascadeEnabled() {
        return Boolean.TRUE.equals(cascade);
    }

}

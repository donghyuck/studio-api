package studio.one.platform.workspace.web.dto.request;

public class WorkspaceActivateRequest {

    private Boolean cascade;

    public WorkspaceActivateRequest() {
    }

    public WorkspaceActivateRequest(
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

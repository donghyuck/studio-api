package studio.one.platform.workspace.web.dto.request;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public class WorkspaceUpdateRequest {

    private String name;

    private WorkspaceVisibility visibility;

    public WorkspaceUpdateRequest() {
    }

    public WorkspaceUpdateRequest(
            String name,
            WorkspaceVisibility visibility) {
        this.name = name;
        this.visibility = visibility;
    }

    public String getName() { return name; }

    public String name() { return name; }

    public WorkspaceVisibility getVisibility() { return visibility; }

    public WorkspaceVisibility visibility() { return visibility; }

    public void setName(String name) { this.name = name; }


    public void setVisibility(WorkspaceVisibility visibility) { this.visibility = visibility; }


}

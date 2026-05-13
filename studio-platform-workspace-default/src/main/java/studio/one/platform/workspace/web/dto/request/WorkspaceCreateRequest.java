package studio.one.platform.workspace.web.dto.request;

import javax.validation.constraints.NotBlank;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public class WorkspaceCreateRequest {

    private Long companyId;

    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private WorkspaceVisibility visibility;

    public WorkspaceCreateRequest() {
    }

    public WorkspaceCreateRequest(
            Long companyId,
            String name,
            String slug,
            WorkspaceVisibility visibility) {
        this.companyId = companyId;
        this.name = name;
        this.slug = slug;
        this.visibility = visibility;
    }

    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long companyId() { return companyId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String name() { return name; }

    public String getSlug() { return slug; }

    public void setSlug(String slug) { this.slug = slug; }

    public String slug() { return slug; }

    public WorkspaceVisibility getVisibility() { return visibility; }

    public void setVisibility(WorkspaceVisibility visibility) { this.visibility = visibility; }

    public WorkspaceVisibility visibility() { return visibility; }

public WorkspaceCreateRequest(String name, String slug, WorkspaceVisibility visibility) {
        this(null, name, slug, visibility);
    }

}

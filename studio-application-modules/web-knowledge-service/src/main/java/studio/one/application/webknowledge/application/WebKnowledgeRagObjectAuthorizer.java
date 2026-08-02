package studio.one.application.webknowledge.application;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;

public class WebKnowledgeRagObjectAuthorizer implements RagObjectAuthorizer {

    private final WebKnowledgeSourceJpaRepository sources;
    private final PrincipalResolver principalResolver;
    private final WorkspacePermissionService permissions;

    public WebKnowledgeRagObjectAuthorizer(
            WebKnowledgeSourceJpaRepository sources,
            PrincipalResolver principalResolver,
            WorkspacePermissionService permissions) {
        this.sources = sources;
        this.principalResolver = principalResolver;
        this.permissions = permissions;
    }

    @Override
    public boolean supports(String objectType) {
        return WebKnowledgeRagIndexJobSourceExecutor.SOURCE_TYPE.equalsIgnoreCase(objectType);
    }

    @Override
    public boolean canRead(String objectType, String objectId) {
        if (!supports(objectType) || objectId == null || objectId.isBlank()) {
            return false;
        }
        var principal = principalResolver.currentOrNull();
        if (principal == null || principal.getUserId() == null) {
            return false;
        }
        return sources.findById(objectId)
                .filter(source -> !source.archived())
                .map(source -> permissions.isGranted(
                        source.workspaceId(), principal.getUserId(), WorkspacePermissionActions.READ))
                .orElse(false);
    }
}

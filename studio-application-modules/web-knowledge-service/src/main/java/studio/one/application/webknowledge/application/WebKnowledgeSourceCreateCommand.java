package studio.one.application.webknowledge.application;

public record WebKnowledgeSourceCreateCommand(
        Long workspaceId,
        String url,
        String displayName,
        String embeddingDeploymentId,
        String requestedBy,
        String collectionMode,
        WebCrawlPolicyInput crawlPolicy) {

    public WebKnowledgeSourceCreateCommand(
            Long workspaceId,
            String url,
            String displayName,
            String embeddingDeploymentId,
            String requestedBy) {
        this(workspaceId, url, displayName, embeddingDeploymentId, requestedBy, null,
                WebCrawlPolicyInput.defaults());
    }
}

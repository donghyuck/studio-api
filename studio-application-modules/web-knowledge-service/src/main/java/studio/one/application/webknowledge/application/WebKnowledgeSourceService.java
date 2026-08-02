package studio.one.application.webknowledge.application;

import java.util.List;

public interface WebKnowledgeSourceService {

    WebKnowledgeSourceView create(Long workspaceId, WebKnowledgeSourceCreateCommand command, String principal);

    List<WebKnowledgeSourceView> list(Long workspaceId, String embeddingDeploymentId);

    WebKnowledgeSourceView get(Long workspaceId, String sourceId);

    WebKnowledgeSourceView refresh(Long workspaceId, String sourceId);

    WebKnowledgeSourceView cancel(Long workspaceId, String sourceId);

    List<WebKnowledgeCrawlRunView> listCrawlRuns(Long workspaceId, String sourceId);

    WebKnowledgeCrawlRunView getCrawlRun(Long workspaceId, String sourceId, String runId);

    List<WebKnowledgePageView> listPages(Long workspaceId, String sourceId);

    WebKnowledgeSourceView updateCrawlPolicy(
            Long workspaceId,
            String sourceId,
            WebCrawlPolicyInput policy);

    void archive(Long workspaceId, String sourceId);
}

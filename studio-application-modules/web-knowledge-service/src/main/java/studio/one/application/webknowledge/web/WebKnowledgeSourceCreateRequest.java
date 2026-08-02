package studio.one.application.webknowledge.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebKnowledgeSourceCreateRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 300) String displayName,
        @NotBlank @Size(max = 160) String embeddingDeploymentId,
        @Size(max = 32) String collectionMode,
        @jakarta.validation.Valid WebCrawlPolicyRequest crawlPolicy) {

    public WebKnowledgeSourceCreateRequest(
            String url,
            String displayName,
            String embeddingDeploymentId) {
        this(url, displayName, embeddingDeploymentId, null, null);
    }
}

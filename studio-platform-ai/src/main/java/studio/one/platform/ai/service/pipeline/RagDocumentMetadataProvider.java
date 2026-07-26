package studio.one.platform.ai.service.pipeline;

import java.util.List;

import studio.one.platform.ai.core.rag.RagSearchResult;

/**
 * Provides source-verified document metadata without scanning the vector store.
 */
public interface RagDocumentMetadataProvider {

    boolean supports(String objectType);

    List<RagSearchResult> find(String objectType, String objectId);
}

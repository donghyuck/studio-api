package studio.one.platform.ai.service.pipeline;

import java.util.Optional;

import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;

/**
 * Resolves display names for source-based RAG index jobs without coupling ai-web to source modules.
 */
public interface RagIndexJobSourceNameResolver {

    boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest);

    Optional<String> resolveSourceName(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest);
}

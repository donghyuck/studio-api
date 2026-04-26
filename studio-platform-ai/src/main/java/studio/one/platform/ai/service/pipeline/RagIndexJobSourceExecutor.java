package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;

/**
 * Executes non-text RAG index job sources without coupling ai core to source-specific modules.
 */
public interface RagIndexJobSourceExecutor {

    boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest);

    void execute(
            RagIndexJob job,
            RagIndexJobCreateRequest request,
            RagIndexJobSourceRequest sourceRequest,
            RagIndexProgressListener listener);
}

package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;

/**
 * Executes non-text RAG index job sources without coupling ai core to source-specific modules.
 */
public interface RagIndexJobSourceExecutor {

    boolean supports(RagIndexJobCreateRequest request);

    void execute(RagIndexJob job, RagIndexJobCreateRequest request, RagIndexProgressListener listener);
}

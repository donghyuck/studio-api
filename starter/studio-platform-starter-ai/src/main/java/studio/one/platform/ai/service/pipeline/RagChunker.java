package studio.one.platform.ai.service.pipeline;

import java.util.List;

import studio.one.platform.ai.core.rag.RagIndexRequest;

interface RagChunker {

    List<RagPipelineChunk> chunk(String indexedText, RagIndexRequest request);
}

package studio.one.platform.ai.service.pipeline;

public interface RagEmbeddingProfileResolver {

    ResolvedRagEmbedding resolve(RagEmbeddingSelection selection);
}

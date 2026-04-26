package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.embedding.EmbeddingPort;

public class SinglePortRagEmbeddingProfileResolver implements RagEmbeddingProfileResolver {

    private final EmbeddingPort embeddingPort;

    public SinglePortRagEmbeddingProfileResolver(EmbeddingPort embeddingPort) {
        this.embeddingPort = java.util.Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    @Override
    public ResolvedRagEmbedding resolve(RagEmbeddingSelection selection) {
        RagEmbeddingSelection resolved = selection == null
                ? new RagEmbeddingSelection(null, null, null, null)
                : selection;
        return new ResolvedRagEmbedding(
                embeddingPort,
                resolved.profileId(),
                resolved.provider(),
                resolved.model(),
                null,
                resolved.inputType());
    }
}

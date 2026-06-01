package studio.one.platform.ai.adapters.vector.mybatis;

import com.pgvector.PGvector;

public final class PgVectorChunkParameter {

    private final String objectType;
    private final String objectId;
    private final int chunkIndex;
    private final String text;
    private final String metadata;
    private final PGvector embedding;
    private final int embeddingDimension;

    public PgVectorChunkParameter(
            String objectType,
            String objectId,
            int chunkIndex,
            String text,
            String metadata,
            PGvector embedding,
            int embeddingDimension) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.metadata = metadata;
        this.embedding = embedding;
        this.embeddingDimension = embeddingDimension;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getText() {
        return text;
    }

    public String getMetadata() {
        return metadata;
    }

    public PGvector getEmbedding() {
        return embedding;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
}

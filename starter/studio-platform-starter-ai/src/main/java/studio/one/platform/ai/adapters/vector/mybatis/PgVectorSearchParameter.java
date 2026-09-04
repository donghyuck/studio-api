package studio.one.platform.ai.adapters.vector.mybatis;

import com.pgvector.PGvector;
import java.util.List;

public class PgVectorSearchParameter {

    private final PGvector vector;
    private final int embeddingDimension;
    private final int limit;
    private final String objectType;
    private final String objectId;
    private final List<String> objectTypes;
    private final List<String> objectIds;
    private final String metadataObjectType;
    private final String metadataObjectId;
    private final List<PgVectorMetadataEqualsCriterion> equalsCriteria;
    private final List<PgVectorMetadataInCriterion> inCriteria;
    private final boolean includeText;
    private final boolean includeMetadata;
    private final boolean minimalMetadata;

    public PgVectorSearchParameter(
            PGvector vector,
            int embeddingDimension,
            int limit,
            String objectType,
            String objectId,
            String metadataObjectType,
            String metadataObjectId,
            List<PgVectorMetadataEqualsCriterion> equalsCriteria,
            List<PgVectorMetadataInCriterion> inCriteria) {
        this(vector, embeddingDimension, limit, objectType, objectId, List.of(), List.of(), metadataObjectType, metadataObjectId,
                equalsCriteria, inCriteria, true, true, false);
    }

    public PgVectorSearchParameter(
            PGvector vector,
            int embeddingDimension,
            int limit,
            String objectType,
            String objectId,
            List<String> objectTypes,
            List<String> objectIds,
            String metadataObjectType,
            String metadataObjectId,
            List<PgVectorMetadataEqualsCriterion> equalsCriteria,
            List<PgVectorMetadataInCriterion> inCriteria,
            boolean includeText,
            boolean includeMetadata,
            boolean minimalMetadata) {
        this.vector = vector;
        this.embeddingDimension = embeddingDimension;
        this.limit = limit;
        this.objectType = objectType;
        this.objectId = objectId;
        this.objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
        this.objectIds = objectIds == null ? List.of() : List.copyOf(objectIds);
        this.metadataObjectType = metadataObjectType;
        this.metadataObjectId = metadataObjectId;
        this.equalsCriteria = equalsCriteria == null ? List.of() : List.copyOf(equalsCriteria);
        this.inCriteria = inCriteria == null ? List.of() : List.copyOf(inCriteria);
        this.includeText = includeText;
        this.includeMetadata = includeMetadata;
        this.minimalMetadata = minimalMetadata;
    }

    public PGvector getVector() {
        return vector;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public int getLimit() {
        return limit;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public List<String> getObjectTypes() {
        return objectTypes;
    }

    public List<String> getObjectIds() {
        return objectIds;
    }

    public String getMetadataObjectType() {
        return metadataObjectType;
    }

    public String getMetadataObjectId() {
        return metadataObjectId;
    }

    public List<PgVectorMetadataEqualsCriterion> getEqualsCriteria() {
        return equalsCriteria;
    }

    public List<PgVectorMetadataInCriterion> getInCriteria() {
        return inCriteria;
    }

    public boolean isIncludeText() {
        return includeText;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public boolean isMinimalMetadata() {
        return minimalMetadata;
    }
}

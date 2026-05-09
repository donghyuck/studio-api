package studio.one.platform.ai.adapters.vector.mybatis;

import com.pgvector.PGvector;
import java.util.List;

public class PgVectorSearchParameter {

    private final PGvector vector;
    private final int limit;
    private final String objectType;
    private final String objectId;
    private final String metadataObjectType;
    private final String metadataObjectId;
    private final List<PgVectorMetadataEqualsCriterion> equalsCriteria;
    private final List<PgVectorMetadataInCriterion> inCriteria;

    public PgVectorSearchParameter(
            PGvector vector,
            int limit,
            String objectType,
            String objectId,
            String metadataObjectType,
            String metadataObjectId,
            List<PgVectorMetadataEqualsCriterion> equalsCriteria,
            List<PgVectorMetadataInCriterion> inCriteria) {
        this.vector = vector;
        this.limit = limit;
        this.objectType = objectType;
        this.objectId = objectId;
        this.metadataObjectType = metadataObjectType;
        this.metadataObjectId = metadataObjectId;
        this.equalsCriteria = equalsCriteria == null ? List.of() : List.copyOf(equalsCriteria);
        this.inCriteria = inCriteria == null ? List.of() : List.copyOf(inCriteria);
    }

    public PGvector getVector() {
        return vector;
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
}

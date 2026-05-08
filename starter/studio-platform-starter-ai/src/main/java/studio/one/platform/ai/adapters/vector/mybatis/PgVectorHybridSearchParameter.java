package studio.one.platform.ai.adapters.vector.mybatis;

import com.pgvector.PGvector;
import java.util.List;

public final class PgVectorHybridSearchParameter extends PgVectorSearchParameter {

    private final String query;
    private final double vectorWeight;
    private final double lexicalWeight;

    public PgVectorHybridSearchParameter(
            PGvector vector,
            int limit,
            String objectType,
            String objectId,
            String metadataObjectType,
            String metadataObjectId,
            List<PgVectorMetadataEqualsCriterion> equalsCriteria,
            List<PgVectorMetadataInCriterion> inCriteria,
            String query,
            double vectorWeight,
            double lexicalWeight) {
        super(vector, limit, objectType, objectId, metadataObjectType, metadataObjectId, equalsCriteria, inCriteria);
        this.query = query;
        this.vectorWeight = vectorWeight;
        this.lexicalWeight = lexicalWeight;
    }

    public String getQuery() {
        return query;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public double getLexicalWeight() {
        return lexicalWeight;
    }
}

package studio.one.platform.ai.adapters.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorChunkParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorHybridSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMetadataEqualsCriterion;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMetadataInCriterion;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchRow;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;

/**
 * PgVector {@link VectorStorePort} implementation backed by the MyBatis
 * {@link PgVectorMapper}.
 */
@Slf4j
public class PgVectorStoreAdapterV2 implements VectorStorePort {

    private final PgVectorMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public PgVectorStoreAdapterV2(PgVectorMapper mapper) {
        this(mapper, null);
    }

    /**
     * @deprecated since 2026-05-08. Prefer the MyBatis-backed
     * {@link #PgVectorStoreAdapterV2(PgVectorMapper, DataSource)} constructor.
     * This constructor remains as a JDBC-only compatibility path and does not use
     * external SQL mapper injection.
     */
    @Deprecated(forRemoval = false)
    public PgVectorStoreAdapterV2(JdbcTemplate jdbcTemplate) {
        this(new PgVectorJdbcMapper(jdbcTemplate), jdbcTemplate.getDataSource());
    }

    public PgVectorStoreAdapterV2(PgVectorMapper mapper, DataSource dataSource) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.transactionTemplate = dataSource == null
                ? null
                : new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void upsert(List<VectorDocument> documents) {
        upsertInternal(documents);
    }

    private void upsertInternal(List<VectorDocument> documents) {
        for (VectorDocument document : documents) {
            Map<String, Object> metadata = withDocumentId(document);
            mapper.upsertChunk(new PgVectorChunkParameter(
                    resolveObjectType(metadata),
                    resolveObjectId(metadata, document.id()),
                    resolveChunkIndex(metadata),
                    document.content(),
                    Json.write(metadata),
                    toPgVector(document.embedding()),
                    document.embedding().size()));
        }
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        return mapper.search(searchParameter(request, true, null, null)).stream()
                .map(PgVectorStoreAdapterV2::mapSearchRow)
                .toList();
    }

    @Override
    public void deleteByObject(String objectType, String objectId) {
        mapper.deleteByObject(objectType, objectId);
    }

    @Override
    public void replaceByObject(String objectType, String objectId, List<VectorDocument> documents) {
        if (transactionTemplate == null) {
            log.warn("TransactionTemplate unavailable; replaceByObject will execute non-atomically. "
                    + "Configure a DataSource to enable transactional replacement.");
            deleteByObject(objectType, objectId);
            upsertInternal(documents);
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            deleteByObject(objectType, objectId);
            upsertInternal(documents);
        });
    }

    @Override
    public List<VectorSearchResult> searchByObject(String objectType, String objectId, VectorSearchRequest request) {
        return mapper.searchByObject(searchParameter(request, false, normalize(objectType), normalize(objectId))).stream()
                .map(PgVectorStoreAdapterV2::mapSearchRow)
                .toList();
    }

    @Override
    public List<VectorSearchResult> hybridSearch(
            String query,
            VectorSearchRequest request,
            double vectorWeight,
            double lexicalWeight) {
        return mapper.hybridSearch(hybridSearchParameter(
                query,
                request,
                vectorWeight,
                lexicalWeight,
                true,
                null,
                null)).stream()
                .map(PgVectorStoreAdapterV2::mapSearchRow)
                .toList();
    }

    @Override
    public List<VectorSearchResult> hybridSearchByObject(
            String query,
            String objectType,
            String objectId,
            VectorSearchRequest request,
            double vectorWeight,
            double lexicalWeight) {
        return mapper.hybridSearchByObject(hybridSearchParameter(
                query,
                request,
                vectorWeight,
                lexicalWeight,
                false,
                normalize(objectType),
                normalize(objectId))).stream()
                .map(PgVectorStoreAdapterV2::mapSearchRow)
                .toList();
    }

    @Override
    public boolean exists(String objectType, String objectId) {
        return mapper.exists(objectType, objectId) > 0;
    }

    @Override
    public List<VectorSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        int rowLimit = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        return mapper.listByObject(objectType, objectId, rowLimit).stream()
                .map(PgVectorStoreAdapterV2::mapListRow)
                .toList();
    }

    @Override
    public List<VectorSearchResult> listByObject(String objectType, String objectId, int offset, int limit) {
        int rowOffset = Math.max(0, offset);
        int rowLimit = limit <= 0 ? 50 : limit;
        return mapper.listByObjectPage(objectType, objectId, rowOffset, rowLimit).stream()
                .map(PgVectorStoreAdapterV2::mapListRow)
                .toList();
    }

    @Override
    public List<VectorSearchResult> listByObject(
            String objectType,
            String objectId,
            String documentId,
            String query,
            int offset,
            int limit) {
        int rowOffset = Math.max(0, offset);
        int rowLimit = limit <= 0 ? 50 : limit;
        return mapper.listByObjectPageFiltered(
                        objectType,
                        objectId,
                        normalize(documentId),
                        normalize(query),
                        rowOffset,
                        rowLimit)
                .stream()
                .map(PgVectorStoreAdapterV2::mapListRow)
                .toList();
    }

    @Override
    public Map<String, Object> getMetadata(String objectType, String objectId) {
        String metadata = mapper.metadataByObject(objectType, objectId);
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        return Map.copyOf(Json.read(metadata));
    }

    private static VectorSearchResult mapSearchRow(PgVectorSearchRow row) {
        double distance = row.getDistance() == null ? 0.0d : row.getDistance();
        return mapRow(row, 1.0d / (1.0d + distance));
    }

    private static VectorSearchResult mapListRow(PgVectorSearchRow row) {
        return mapRow(row, 1.0d);
    }

    private static VectorSearchResult mapRow(PgVectorSearchRow row, double score) {
        String objectId = row.getObjectId();
        Map<String, Object> metadata = new HashMap<>(Json.read(row.getMetadata()));
        if (row.getId() != null) {
            metadata.putIfAbsent("_vectorRowId", "row-" + row.getId());
        }
        String documentId = Objects.toString(metadata.getOrDefault("documentId", objectId), objectId);
        VectorDocument document = new VectorDocument(documentId, row.getText(), metadata, List.of());
        return new VectorSearchResult(document, score);
    }

    private static PgVectorSearchParameter searchParameter(
            VectorSearchRequest request,
            boolean includeObjectColumns,
            String objectType,
            String objectId) {
        MetadataFilter filter = request.metadataFilter();
        return new PgVectorSearchParameter(
                toPgVector(request.embedding()),
                request.embedding().size(),
                request.topK(),
                objectType,
                objectId,
                includeObjectColumns ? filter.objectType() : null,
                includeObjectColumns ? filter.objectId() : null,
                equalsCriteria(filter),
                inCriteria(filter));
    }

    private static PgVectorHybridSearchParameter hybridSearchParameter(
            String query,
            VectorSearchRequest request,
            double vectorWeight,
            double lexicalWeight,
            boolean includeObjectColumns,
            String objectType,
            String objectId) {
        MetadataFilter filter = request.metadataFilter();
        return new PgVectorHybridSearchParameter(
                toPgVector(request.embedding()),
                request.embedding().size(),
                request.topK(),
                objectType,
                objectId,
                includeObjectColumns ? filter.objectType() : null,
                includeObjectColumns ? filter.objectId() : null,
                equalsCriteria(filter),
                inCriteria(filter),
                query,
                vectorWeight,
                lexicalWeight);
    }

    private static List<PgVectorMetadataEqualsCriterion> equalsCriteria(MetadataFilter filter) {
        return filter.equalsCriteria().entrySet().stream()
                .filter(entry -> !isObjectScopeKey(entry.getKey()))
                .map(entry -> new PgVectorMetadataEqualsCriterion(
                        entry.getKey(),
                        Objects.toString(entry.getValue(), null)))
                .toList();
    }

    private static List<PgVectorMetadataInCriterion> inCriteria(MetadataFilter filter) {
        return filter.inCriteria().entrySet().stream()
                .filter(entry -> !isObjectScopeKey(entry.getKey()))
                .map(entry -> new PgVectorMetadataInCriterion(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(value -> Objects.toString(value, null))
                                .toList()))
                .toList();
    }

    private static PGvector toPgVector(List<Double> embedding) {
        float[] values = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            values[i] = embedding.get(i).floatValue();
        }
        return new PGvector(values);
    }

    private static String resolveObjectType(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("objectType", "DEFAULT");
        return Objects.toString(value, "DEFAULT");
    }

    private static String resolveObjectId(Map<String, Object> metadata, String fallback) {
        Object value = metadata.get("objectId");
        if (value != null && !Objects.toString(value, "").isBlank()) {
            return Objects.toString(value);
        }
        return fallback;
    }

    private static int resolveChunkIndex(Map<String, Object> metadata) {
        Object value = metadata.getOrDefault("chunkOrder", metadata.getOrDefault("chunkIndex", 0));
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Map<String, Object> withDocumentId(VectorDocument document) {
        Map<String, Object> metadata = new HashMap<>(document.metadata());
        metadata.putIfAbsent("documentId", document.id());
        return metadata;
    }

    private static boolean isObjectScopeKey(String key) {
        return "objectType".equals(key) || "objectId".equals(key);
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static final class Json {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        private Json() {
        }

        private static String write(Map<String, Object> metadata) {
            try {
                return objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize metadata", e);
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> read(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize metadata", e);
            }
        }
    }
}

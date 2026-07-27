package studio.one.platform.ai.service.pipeline;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;
import studio.one.platform.chunking.artifact.ChunkSet;
import studio.one.platform.chunking.artifact.ChunkSetItem;
import studio.one.platform.chunking.artifact.ChunkSetQualityStatus;
import studio.one.platform.chunking.artifact.ChunkSetStatus;
import studio.one.platform.chunking.artifact.ChunkSetStore;

public class JdbcChunkSetStore implements ChunkSetStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };
    static final int DEFAULT_INSERT_BATCH_SIZE = 25;
    static final int MAX_INSERT_BATCH_SIZE = 200;

    private final NamedParameterJdbcTemplate template;
    private final ObjectMapper objectMapper;
    private final int insertBatchSize;
    private final TransactionOperations transactions;

    public JdbcChunkSetStore(
            NamedParameterJdbcTemplate template, ObjectMapper objectMapper, TransactionOperations transactions) {
        this(template, objectMapper, DEFAULT_INSERT_BATCH_SIZE, transactions);
    }

    public JdbcChunkSetStore(
            NamedParameterJdbcTemplate template,
            ObjectMapper objectMapper,
            int insertBatchSize,
            TransactionOperations transactions) {
        this.template = Objects.requireNonNull(template, "template");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.insertBatchSize = normalizeBatchSize(insertBatchSize);
        this.transactions = transactions;
    }

    @Override
    public ChunkSet save(ChunkSet chunkSet) {
        Objects.requireNonNull(chunkSet, "chunkSet");
        runInTransaction(() -> write(chunkSet));
        return chunkSet;
    }

    @Override
    public Optional<ChunkSet> findById(String chunkSetId) {
        List<Header> rows = template.query("""
                SELECT chunk_set_id, object_type, object_id, document_id, source_revision_id,
                       source_content_hash, strategy, strategy_hash, chunk_unit, max_size, overlap_size,
                       status, quality_status, quality_issues, metadata, created_at, updated_at
                  FROM tb_ai_chunk_set
                 WHERE chunk_set_id = :chunkSetId
                """, Map.of("chunkSetId", chunkSetId), this::mapHeader);
        return rows.stream().findFirst().map(this::toChunkSet);
    }

    @Override
    public Optional<ChunkSet> findLatest(
            String objectType, String objectId, String documentId, String sourceRevisionId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", objectType)
                .addValue("objectId", objectId)
                .addValue("documentId", documentId)
                .addValue("sourceRevisionId", sourceRevisionId);
        List<Header> rows = template.query("""
                SELECT chunk_set_id, object_type, object_id, document_id, source_revision_id,
                       source_content_hash, strategy, strategy_hash, chunk_unit, max_size, overlap_size,
                       status, quality_status, quality_issues, metadata, created_at, updated_at
                  FROM tb_ai_chunk_set
                 WHERE object_type = :objectType
                   AND object_id = :objectId
                   AND document_id = :documentId
                   AND source_revision_id = :sourceRevisionId
                 ORDER BY updated_at DESC
                 LIMIT 1
                """, params, this::mapHeader);
        return rows.stream().findFirst().map(this::toChunkSet);
    }

    @Override
    public void invalidate(String chunkSetId) {
        template.update("""
                UPDATE tb_ai_chunk_set
                   SET status = :status, updated_at = CURRENT_TIMESTAMP
                 WHERE chunk_set_id = :chunkSetId
                """, Map.of("status", ChunkSetStatus.INVALIDATED.name(), "chunkSetId", chunkSetId));
    }

    private void write(ChunkSet chunkSet) {
        Map<String, ?> id = Map.of("chunkSetId", chunkSet.chunkSetId());
        template.update("DELETE FROM tb_ai_chunk_item WHERE chunk_set_id = :chunkSetId", id);
        template.update("DELETE FROM tb_ai_chunk_set WHERE chunk_set_id = :chunkSetId", id);
        template.update("""
                INSERT INTO tb_ai_chunk_set(
                    chunk_set_id, object_type, object_id, document_id, source_revision_id,
                    source_content_hash, strategy, strategy_hash, chunk_unit, max_size, overlap_size,
                    status, quality_status, quality_issues, metadata, created_at, updated_at)
                VALUES (
                    :chunkSetId, :objectType, :objectId, :documentId, :sourceRevisionId,
                    :sourceContentHash, :strategy, :strategyHash, :chunkUnit, :maxSize, :overlapSize,
                    :status, :qualityStatus, :qualityIssues, :metadata, :createdAt, :updatedAt)
                """, headerParams(chunkSet));
        for (int offset = 0; offset < chunkSet.items().size(); offset += insertBatchSize) {
            List<ChunkSetItem> slice = chunkSet.items().subList(
                    offset, Math.min(offset + insertBatchSize, chunkSet.items().size()));
            MapSqlParameterSource[] batch = slice.stream()
                    .map(item -> itemParams(chunkSet.chunkSetId(), item))
                    .toArray(MapSqlParameterSource[]::new);
            template.batchUpdate("""
                    INSERT INTO tb_ai_chunk_item(
                        chunk_set_id, chunk_index, chunk_id, text, content_hash, metadata, created_at)
                    VALUES (
                        :chunkSetId, :chunkIndex, :chunkId, :text, :contentHash, :metadata, :createdAt)
                    """, batch);
        }
    }

    private int normalizeBatchSize(int requestedBatchSize) {
        if (requestedBatchSize <= 0) {
            return DEFAULT_INSERT_BATCH_SIZE;
        }
        return Math.min(requestedBatchSize, MAX_INSERT_BATCH_SIZE);
    }

    private MapSqlParameterSource headerParams(ChunkSet value) {
        return new MapSqlParameterSource()
                .addValue("chunkSetId", value.chunkSetId())
                .addValue("objectType", value.objectType())
                .addValue("objectId", value.objectId())
                .addValue("documentId", value.documentId())
                .addValue("sourceRevisionId", value.sourceRevisionId())
                .addValue("sourceContentHash", value.sourceContentHash())
                .addValue("strategy", value.strategy())
                .addValue("strategyHash", value.strategyHash())
                .addValue("chunkUnit", value.chunkUnit())
                .addValue("maxSize", value.maxSize())
                .addValue("overlapSize", value.overlap())
                .addValue("status", value.status().name())
                .addValue("qualityStatus", value.qualityStatus().name())
                .addValue("qualityIssues", writeJson(value.qualityIssues()))
                .addValue("metadata", writeJson(value.metadata()))
                .addValue("createdAt", Timestamp.from(value.createdAt()))
                .addValue("updatedAt", Timestamp.from(value.updatedAt()));
    }

    private MapSqlParameterSource itemParams(String chunkSetId, ChunkSetItem item) {
        return new MapSqlParameterSource()
                .addValue("chunkSetId", chunkSetId)
                .addValue("chunkIndex", item.chunkIndex())
                .addValue("chunkId", item.chunkId())
                .addValue("text", item.text())
                .addValue("contentHash", item.contentHash())
                .addValue("metadata", writeJson(item.metadata()))
                .addValue("createdAt", Timestamp.from(java.time.Instant.now()));
    }

    private Header mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new Header(
                rs.getString("chunk_set_id"), rs.getString("object_type"), rs.getString("object_id"),
                rs.getString("document_id"), rs.getString("source_revision_id"),
                rs.getString("source_content_hash"), rs.getString("strategy"), rs.getString("strategy_hash"),
                rs.getString("chunk_unit"), integer(rs, "max_size"), integer(rs, "overlap_size"),
                ChunkSetStatus.valueOf(rs.getString("status")),
                ChunkSetQualityStatus.valueOf(rs.getString("quality_status")),
                readList(rs.getString("quality_issues")), readMap(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private ChunkSet toChunkSet(Header header) {
        List<ChunkSetItem> items = template.query("""
                SELECT chunk_index, chunk_id, text, content_hash, metadata
                  FROM tb_ai_chunk_item
                 WHERE chunk_set_id = :chunkSetId
                 ORDER BY chunk_index
                """, Map.of("chunkSetId", header.chunkSetId()), (itemRs, itemRow) -> new ChunkSetItem(
                        itemRs.getInt("chunk_index"), itemRs.getString("chunk_id"), itemRs.getString("text"),
                        itemRs.getString("content_hash"), readMap(itemRs.getString("metadata"))));
        return new ChunkSet(
                header.chunkSetId(), header.objectType(), header.objectId(), header.documentId(),
                header.sourceRevisionId(), header.sourceContentHash(), header.strategy(), header.strategyHash(),
                header.chunkUnit(), header.maxSize(), header.overlap(), header.status(), header.qualityStatus(),
                header.qualityIssues(), header.metadata(), items, header.createdAt(), header.updatedAt());
    }

    private Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize ChunkSet JSON", ex);
        }
    }

    private Map<String, Object> readMap(String value) {
        return readJson(value, "{}", MAP_TYPE);
    }

    private List<String> readList(String value) {
        return readJson(value, "[]", LIST_TYPE);
    }

    private <T> T readJson(String value, String emptyJson, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? emptyJson : value, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to deserialize ChunkSet JSON", ex);
        }
    }

    private void runInTransaction(Runnable action) {
        if (transactions == null) {
            action.run();
        } else {
            transactions.executeWithoutResult(status -> action.run());
        }
    }

    private record Header(
            String chunkSetId,
            String objectType,
            String objectId,
            String documentId,
            String sourceRevisionId,
            String sourceContentHash,
            String strategy,
            String strategyHash,
            String chunkUnit,
            Integer maxSize,
            Integer overlap,
            ChunkSetStatus status,
            ChunkSetQualityStatus qualityStatus,
            List<String> qualityIssues,
            Map<String, Object> metadata,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }
}

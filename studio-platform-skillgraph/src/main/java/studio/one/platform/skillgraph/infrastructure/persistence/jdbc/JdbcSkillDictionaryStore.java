package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

@RequiredArgsConstructor
public class JdbcSkillDictionaryStore implements SkillDictionaryStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillDictionary save(SkillDictionary skill) {
        int updated = template.update("""
                UPDATE tb_skill_dictionary
                SET name = :name,
                    normalized_name = :normalizedName,
                    category_id = :categoryId,
                    skill_type = :skillType,
                    status = :status,
                    updated_at = :updatedAt
                WHERE skill_id = :skillId
                """, skillParams(skill));
        if (updated > 0) {
            return skill;
        }
        template.update("""
                INSERT INTO tb_skill_dictionary
                    (skill_id, name, normalized_name, category_id, skill_type, status, created_at, updated_at)
                VALUES
                    (:skillId, :name, :normalizedName, :categoryId, :skillType, :status, :createdAt, :updatedAt)
                """, skillParams(skill));
        return skill;
    }

    @Override
    public Optional<SkillDictionary> findById(String skillId) {
        return queryOne("SELECT * FROM tb_skill_dictionary WHERE skill_id = :skillId", Map.of("skillId", skillId));
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedName(String normalizedName) {
        return queryOne("SELECT * FROM tb_skill_dictionary WHERE normalized_name = :normalizedName",
                Map.of("normalizedName", normalizedName));
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedAlias(String normalizedAlias) {
        return queryOne("""
                SELECT d.*
                FROM tb_skill_dictionary d
                JOIN tb_skill_alias a ON a.skill_id = d.skill_id
                WHERE a.normalized_alias = :normalizedAlias
                """, Map.of("normalizedAlias", normalizedAlias));
    }

    @Override
    public SkillDictionary saveEmbedding(String skillId, List<Double> embedding, String embeddingModel) {
        return saveEmbedding(skillId, null, embeddingModel, embedding);
    }

    @Override
    public SkillDictionary saveEmbedding(
            String skillId,
            String embeddingProvider,
            String embeddingModel,
            List<Double> embedding) {
        SkillDictionary skill = findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        if (embedding == null || embedding.isEmpty()) {
            return skill;
        }
        template.update("""
                UPDATE tb_skill_dictionary
                SET embedding = CAST(:embedding AS vector),
                    updated_at = :updatedAt
                WHERE skill_id = :skillId
                """, new MapSqlParameterSource()
                .addValue("skillId", skillId)
                .addValue("embedding", vectorLiteral(embedding))
                .addValue("updatedAt", Timestamp.from(Instant.now())));
        saveEmbeddingMetadata(skillId, embeddingProvider, embeddingModel, embedding);
        return findById(skillId).orElse(skill);
    }

    private void saveEmbeddingMetadata(
            String skillId,
            String embeddingProvider,
            String embeddingModel,
            List<Double> embedding) {
        String model = embeddingModel == null || embeddingModel.isBlank() ? "unknown" : embeddingModel.trim();
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        template.update("""
                INSERT INTO tb_skill_embedding
                    (embedding_id, source_type, source_id, embedding_provider, embedding_model,
                     embedding_dimension, embedding_text, embedding, created_at)
                VALUES
                    (:embeddingId, 'SKILL_DICTIONARY', :skillId, :embeddingProvider, :embeddingModel,
                     :embeddingDimension, :embeddingText, CAST(:embedding AS vector), :createdAt)
                ON CONFLICT (source_type, source_id, embedding_provider, embedding_model)
                DO UPDATE SET
                    embedding_dimension = EXCLUDED.embedding_dimension,
                    embedding_text = EXCLUDED.embedding_text,
                    embedding = EXCLUDED.embedding,
                    created_at = EXCLUDED.created_at
                """, new MapSqlParameterSource()
                .addValue("embeddingId", "skill_dictionary_embedding_" + skillId + "_" + Integer.toHexString(model.hashCode()))
                .addValue("skillId", skillId)
                .addValue("embeddingProvider", provider)
                .addValue("embeddingModel", model)
                .addValue("embeddingDimension", embedding.size())
                .addValue("embeddingText", skillId)
                .addValue("embedding", vectorLiteral(embedding))
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }

    @Override
    public List<SkillEmbeddingMetadata> findEmbeddingMetadataList(String skillId) {
        return template.query("""
                SELECT embedding_provider, embedding_model, embedding_dimension, created_at
                FROM tb_skill_embedding
                WHERE source_type = 'SKILL_DICTIONARY' AND source_id = :skillId
                ORDER BY created_at DESC
                """, Map.of("skillId", skillId), (rs, rowNum) -> new SkillEmbeddingMetadata(
                rs.getString("embedding_provider"),
                rs.getString("embedding_model"),
                rs.getInt("embedding_dimension"),
                instant(rs.getTimestamp("created_at"))));
    }

    @Override
    public SkillAlias saveAlias(SkillAlias alias) {
        int updated = template.update("""
                UPDATE tb_skill_alias
                SET skill_id = :skillId,
                    alias = :alias,
                    normalized_alias = :normalizedAlias
                WHERE alias_id = :aliasId
                """, aliasParams(alias));
        if (updated > 0) {
            return alias;
        }
        template.update("""
                INSERT INTO tb_skill_alias
                    (alias_id, skill_id, alias, normalized_alias, created_at)
                VALUES
                    (:aliasId, :skillId, :alias, :normalizedAlias, :createdAt)
                """, aliasParams(alias));
        return alias;
    }

    @Override
    public Page<SkillDictionary> search(String q, String status, String categoryId, Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase();
        String statusFilter = status == null ? "" : status.trim();
        String categoryFilter = categoryId == null ? "" : categoryId.trim();
        int limit = pageable.getPageSize();
        long offset = pageable.getOffset();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", query)
                .addValue("likeQ", "%" + query + "%")
                .addValue("status", statusFilter)
                .addValue("categoryId", categoryFilter)
                .addValue("limit", limit)
                .addValue("offset", offset);

        String whereSql = """
                WHERE (:q = '' OR LOWER(name) LIKE :likeQ OR normalized_name LIKE :likeQ)
                  AND (:status = '' OR status = :status)
                  AND (:categoryId = '' OR category_id = :categoryId)
                """;
        String orderSql = dictionaryOrderBy(pageable.getSort());

        List<SkillDictionary> content = template.query("""
                SELECT * FROM tb_skill_dictionary
                """ + whereSql + """
                """ + orderSql + """
                LIMIT :limit OFFSET :offset
                """, params, this::mapSkill);

        Long total = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary
                """ + whereSql,
                params,
                Long.class);

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private String dictionaryOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY name ASC\n";
        }
        List<String> orders = new ArrayList<>();
        sort.forEach(order -> {
            String column = dictionarySortColumn(order.getProperty());
            if (column != null) {
                orders.add(column + (order.isDescending() ? " DESC" : " ASC"));
            }
        });
        if (orders.isEmpty()) {
            return "ORDER BY name ASC\n";
        }
        return "ORDER BY " + String.join(", ", orders) + "\n";
    }

    private String dictionarySortColumn(String property) {
        return switch (property) {
            case "skillId", "skill_id" -> "skill_id";
            case "name", "skillName", "skill_name" -> "name";
            case "normalizedName", "normalized_name" -> "normalized_name";
            case "status" -> "status";
            case "categoryId", "category_id" -> "category_id";
            case "createdAt", "created_at" -> "created_at";
            case "updatedAt", "updated_at" -> "updated_at";
            default -> null;
        };
    }

    @Override
    public List<SkillDictionary> findByCategoryId(String categoryId, int limit) {
        int max = limit <= 0 ? 100 : limit;
        return template.query("""
                SELECT * FROM tb_skill_dictionary
                WHERE category_id = :categoryId
                ORDER BY name
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("limit", max), this::mapSkill);
    }

    @Override
    public int countByCategoryId(String categoryId) {
        Integer count = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary
                WHERE category_id = :categoryId
                """, Map.of("categoryId", categoryId), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<SkillVectorItem> findVectorItems(int limit) {
        return findVectorItems(null, null, null, limit);
    }

    @Override
    public List<SkillVectorItem> findVectorItems(
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            int limit) {
        return findVectorItems(null, embeddingProvider, embeddingModel, embeddingDimension, limit);
    }

    @Override
    public List<SkillVectorItem> findVectorItems(
            String skillType,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            int limit) {
        String limitClause = limit <= 0 ? "" : "LIMIT :limit";
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder filters = new StringBuilder();
        if (skillType != null && !skillType.isBlank()) {
            filters.append(" AND COALESCE(d.skill_type, 'UNKNOWN') = :skillType\n");
            params.addValue("skillType", studio.one.platform.skillgraph.domain.model.SkillType.normalizeName(skillType));
        }
        if (embeddingProvider != null && !embeddingProvider.isBlank()) {
            filters.append(" AND e.embedding_provider = :embeddingProvider\n");
            params.addValue("embeddingProvider", embeddingProvider.trim());
        }
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            filters.append(" AND e.embedding_model = :embeddingModel\n");
            params.addValue("embeddingModel", embeddingModel.trim());
        }
        if (embeddingDimension != null && embeddingDimension > 0) {
            filters.append(" AND e.embedding_dimension = :embeddingDimension\n");
            params.addValue("embeddingDimension", embeddingDimension);
        }
        if (limit > 0) {
            params.addValue("limit", limit);
        }
        return template.query("""
                SELECT d.skill_id,
                       d.name,
                       d.skill_type,
                       e.embedding::text AS embedding_text,
                       e.embedding_model,
                       e.created_at
                FROM tb_skill_dictionary d
                JOIN tb_skill_embedding e
                  ON e.source_type = 'SKILL_DICTIONARY'
                 AND e.source_id = d.skill_id
                WHERE d.status = 'ACTIVE'
                %s
                ORDER BY d.name
                %s
                """.formatted(filters, limitClause), params, (rs, rowNum) -> new SkillVectorItem(
                rs.getString("skill_id"),
                rs.getString("name"),
                rs.getString("skill_type"),
                parseVector(rs.getString("embedding_text")),
                rs.getString("embedding_model"),
                instant(rs.getTimestamp("created_at"))));
    }

    @Override
    public List<SkillDictionary> findMissingEmbeddingSkills(int limit) {
        return findMissingEmbeddingSkills(null, null, limit);
    }

    @Override
    public List<SkillDictionary> findMissingEmbeddingSkills(String embeddingProvider, String embeddingModel, int limit) {
        int max = limit <= 0 ? 100 : limit;
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        String model = embeddingModel == null || embeddingModel.isBlank() ? "unknown" : embeddingModel.trim();
        return template.query("""
                SELECT d.*
                FROM tb_skill_dictionary d
                WHERE d.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tb_skill_embedding e
                      WHERE e.source_type = 'SKILL_DICTIONARY'
                        AND e.source_id = d.skill_id
                        AND e.embedding_provider = :embeddingProvider
                        AND e.embedding_model = :embeddingModel
                  )
                ORDER BY d.name
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("embeddingProvider", provider)
                .addValue("embeddingModel", model)
                .addValue("limit", max), this::mapSkill);
    }

    @Override
    public int countMissingEmbeddingSkills() {
        return countMissingEmbeddingSkills(null, null);
    }

    @Override
    public int countMissingEmbeddingSkills(String embeddingProvider, String embeddingModel) {
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider.trim();
        String model = embeddingModel == null || embeddingModel.isBlank() ? "unknown" : embeddingModel.trim();
        Integer count = template.queryForObject("""
                SELECT COUNT(*)
                FROM tb_skill_dictionary d
                WHERE d.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tb_skill_embedding e
                      WHERE e.source_type = 'SKILL_DICTIONARY'
                        AND e.source_id = d.skill_id
                        AND e.embedding_provider = :embeddingProvider
                        AND e.embedding_model = :embeddingModel
                  )
                """, new MapSqlParameterSource()
                .addValue("embeddingProvider", provider)
                .addValue("embeddingModel", model), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int updateCategory(List<String> skillIds, String categoryId) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET category_id = :categoryId,
                    updated_at = :updatedAt
                WHERE skill_id IN (:skillIds)
                """, new MapSqlParameterSource()
                .addValue("skillIds", skillIds)
                .addValue("categoryId", categoryId)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    @Override
    public int updateCategoryByCategoryIds(List<String> sourceCategoryIds, String targetCategoryId) {
        if (sourceCategoryIds == null || sourceCategoryIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET category_id = :targetCategoryId,
                    updated_at = :updatedAt
                WHERE category_id IN (:sourceCategoryIds)
                """, new MapSqlParameterSource()
                .addValue("sourceCategoryIds", sourceCategoryIds)
                .addValue("targetCategoryId", targetCategoryId)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    @Override
    public int updateStatus(List<String> skillIds, String status) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        return template.update("""
                UPDATE tb_skill_dictionary
                SET status = :status,
                    updated_at = :updatedAt
                WHERE skill_id IN (:skillIds)
                """, new MapSqlParameterSource()
                .addValue("skillIds", skillIds)
                .addValue("status", status)
                .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    private Optional<SkillDictionary> queryOne(String sql, Map<String, ?> params) {
        return template.query(sql, params, this::mapSkill).stream().findFirst();
    }

    private MapSqlParameterSource skillParams(SkillDictionary skill) {
        return new MapSqlParameterSource()
                .addValue("skillId", skill.skillId())
                .addValue("name", skill.name())
                .addValue("normalizedName", skill.normalizedName())
                .addValue("categoryId", skill.categoryId())
                .addValue("skillType", skill.skillType())
                .addValue("status", skill.status())
                .addValue("createdAt", Timestamp.from(skill.createdAt()))
                .addValue("updatedAt", Timestamp.from(skill.updatedAt()));
    }

    private MapSqlParameterSource aliasParams(SkillAlias alias) {
        return new MapSqlParameterSource()
                .addValue("aliasId", alias.aliasId())
                .addValue("skillId", alias.skillId())
                .addValue("alias", alias.alias())
                .addValue("normalizedAlias", alias.normalizedAlias())
                .addValue("createdAt", Timestamp.from(alias.createdAt()));
    }

    private SkillDictionary mapSkill(ResultSet rs, int rowNum) throws SQLException {
        return new SkillDictionary(
                rs.getString("skill_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                rs.getString("category_id"),
                hasColumn(rs, "skill_type") ? rs.getString("skill_type") : null,
                rs.getString("status"),
                hasColumn(rs, "embedding") && rs.getObject("embedding") != null,
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private boolean hasColumn(ResultSet rs, String column) throws SQLException {
        int count = rs.getMetaData().getColumnCount();
        for (int index = 1; index <= count; index++) {
            if (column.equalsIgnoreCase(rs.getMetaData().getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.toString();
    }

    private List<Double> parseVector(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String stripped = value.trim()
                .replace("[", "")
                .replace("]", "");
        if (stripped.isBlank()) {
            return List.of();
        }
        String[] parts = stripped.split(",");
        List<Double> vector = new ArrayList<>(parts.length);
        for (String part : parts) {
            vector.add(Double.parseDouble(part.trim()));
        }
        return vector;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

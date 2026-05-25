package studio.one.platform.skillgraph.infrastructure.skilldataset.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConceptEmbedding;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConceptVectorSearchHit;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDataset;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;

@RequiredArgsConstructor
public class JdbcSkillDatasetStore implements SkillDatasetStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveDataset(SkillDataset dataset) {
        jdbcTemplate.update("""
                insert into tb_skill_dataset (
                    dataset_id, provider, dataset_name, version, language, source_location, imported_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                on conflict (dataset_id) do update set
                    provider = excluded.provider,
                    dataset_name = excluded.dataset_name,
                    version = excluded.version,
                    language = excluded.language,
                    source_location = excluded.source_location,
                    imported_at = excluded.imported_at
                """,
                dataset.datasetId(),
                dataset.provider(),
                dataset.datasetName(),
                dataset.version(),
                dataset.language(),
                dataset.sourceLocation(),
                Timestamp.from(dataset.importedAt()));
    }

    @Override
    public void upsertConcept(SkillConcept concept) {
        upsertConcepts(List.of(concept));
    }

    @Override
    public void upsertRelation(SkillRelation relation) {
        upsertRelations(List.of(relation));
    }

    @Override
    public void upsertConcepts(List<SkillConcept> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                insert into tb_skill_dataset_concept (
                    concept_id, dataset_id, provider, concept_type,
                    external_code, parent_code, preferred_label, description,
                    level_value, category_path, normalized_label, raw_json,
                    created_at, updated_at
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                    current_timestamp, current_timestamp
                )
                on conflict (concept_id) do update set
                    dataset_id = excluded.dataset_id,
                    provider = excluded.provider,
                    concept_type = excluded.concept_type,
                    external_code = excluded.external_code,
                    parent_code = excluded.parent_code,
                    preferred_label = excluded.preferred_label,
                    description = excluded.description,
                    level_value = excluded.level_value,
                    category_path = excluded.category_path,
                    normalized_label = excluded.normalized_label,
                    raw_json = excluded.raw_json,
                    updated_at = current_timestamp
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
                SkillConcept c = concepts.get(i);
                ps.setString(1, c.conceptId());
                ps.setString(2, c.datasetId());
                ps.setString(3, c.provider());
                ps.setString(4, c.conceptType());
                ps.setString(5, c.externalCode());
                ps.setString(6, c.parentCode());
                ps.setString(7, c.preferredLabel());
                ps.setString(8, c.description());
                ps.setString(9, c.levelValue());
                ps.setString(10, c.categoryPath());
                ps.setString(11, c.normalizedLabel());
                ps.setString(12, c.rawJson());
            }

            @Override
            public int getBatchSize() {
                return concepts.size();
            }
        });
    }

    @Override
    public void upsertRelations(List<SkillRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                insert into tb_skill_dataset_relation (
                    relation_id, dataset_id, provider,
                    source_concept_id, target_concept_id,
                    relation_type, confidence, raw_json,
                    created_at
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?::jsonb, current_timestamp
                )
                on conflict (relation_id) do update set
                    dataset_id = excluded.dataset_id,
                    provider = excluded.provider,
                    source_concept_id = excluded.source_concept_id,
                    target_concept_id = excluded.target_concept_id,
                    relation_type = excluded.relation_type,
                    confidence = excluded.confidence,
                    raw_json = excluded.raw_json
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
                SkillRelation r = relations.get(i);
                ps.setString(1, r.relationId());
                ps.setString(2, r.datasetId());
                ps.setString(3, r.provider());
                ps.setString(4, r.sourceConceptId());
                ps.setString(5, r.targetConceptId());
                ps.setString(6, r.relationType());

                if (r.confidence() == null) {
                    ps.setObject(7, null);
                } else {
                    ps.setDouble(7, r.confidence());
                }

                ps.setString(8, r.rawJson());
            }

            @Override
            public int getBatchSize() {
                return relations.size();
            }
        });
    }

    @Override
    public Optional<SkillDataset> findDataset(String datasetId) {
        return jdbcTemplate.query("""
                select dataset_id, provider, dataset_name, version, language, source_location, imported_at
                  from tb_skill_dataset
                 where dataset_id = ?
                """, datasetMapper(), datasetId).stream().findFirst();
    }

    @Override
    public Page<SkillDataset> findDatasets(Pageable pageable) {
        String sql = """
                select dataset_id, provider, dataset_name, version, language, source_location, imported_at
                  from tb_skill_dataset
                """ + orderBy(pageable.getSort(), datasetSortColumns(), "dataset_id desc") + """
                 limit ? offset ?
                """;
        List<SkillDataset> content = jdbcTemplate.query(
                sql,
                datasetMapper(),
                pageable.getPageSize(),
                pageable.getOffset());
        Long total = jdbcTemplate.queryForObject("select count(*) from tb_skill_dataset", Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Optional<SkillConcept> findConcept(String conceptId) {
        return jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where concept_id = ?
                """, conceptMapper(), conceptId).stream().findFirst();
    }

    @Override
    public Optional<SkillConcept> findConcept(String datasetId, String conceptId) {
        return jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where dataset_id = ?
                   and concept_id = ?
                """, conceptMapper(), datasetId, conceptId).stream().findFirst();
    }

    @Override
    public List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        StringBuilder sql = new StringBuilder("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where dataset_id = ?
                """);
        if (conceptType != null) {
            sql.append("   and concept_type = ?\n");
            args.add(conceptType);
        }
        sql.append(" order by concept_type, preferred_label\n limit ?\n");
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), conceptMapper(), args.toArray());
    }

    @Override
    public Page<SkillConcept> findConcepts(String datasetId, String conceptType, Pageable pageable) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        StringBuilder where = new StringBuilder(" where dataset_id = ?\n");
        if (conceptType != null) {
            where.append("   and concept_type = ?\n");
            args.add(conceptType);
        }

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageable.getPageSize());
        queryArgs.add(pageable.getOffset());
        List<SkillConcept> content = jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                """ + where + orderBy(pageable.getSort(), conceptSortColumns(), "concept_id desc") + """
                 limit ? offset ?
                """, conceptMapper(), queryArgs.toArray());
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from tb_skill_dataset_concept" + where,
                Long.class,
                args.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, int limit) {
        String keyword = normalizeQuery(query);
        if (keyword == null) {
            return List.of();
        }

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where 1 = 1
                """);
        if (datasetId != null) {
            sql.append("   and dataset_id = ?\n");
            args.add(datasetId);
        }
        if (conceptType != null) {
            sql.append("   and concept_type = ?\n");
            args.add(conceptType);
        }
        sql.append("""
                   and (
                        preferred_label ilike ?
                        or normalized_label ilike ?
                        or category_path ilike ?
                        or external_code ilike ?
                        or description ilike ?
                   )
                 order by
                       case when normalized_label = lower(?) then 0 else 1 end,
                       concept_type,
                       preferred_label
                 limit ?
                """);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);
        args.add(query.trim());
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), conceptMapper(), args.toArray());
    }

    @Override
    public Page<SkillConcept> searchConcepts(String datasetId, String conceptType, String query, Pageable pageable) {
        String keyword = normalizeQuery(query);
        if (keyword == null) {
            return Page.empty(pageable);
        }

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1\n");
        if (datasetId != null) {
            where.append("   and dataset_id = ?\n");
            args.add(datasetId);
        }
        if (conceptType != null) {
            where.append("   and concept_type = ?\n");
            args.add(conceptType);
        }
        where.append("""
                   and (
                        preferred_label ilike ?
                        or normalized_label ilike ?
                        or category_path ilike ?
                        or external_code ilike ?
                        or description ilike ?
                   )
                """);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);
        args.add(keyword);

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(query.trim());
        queryArgs.add(pageable.getPageSize());
        queryArgs.add(pageable.getOffset());
        List<SkillConcept> content = jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                """ + where + """
                 order by
                       case when normalized_label = lower(?) then 0 else 1 end,
                """ + orderByExpressions(pageable.getSort(), conceptSortColumns(), "concept_id desc") + """
                 limit ? offset ?
                """, conceptMapper(), queryArgs.toArray());
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from tb_skill_dataset_concept" + where,
                Long.class,
                args.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillConcept> findChildConcepts(String datasetId, String conceptId, String relationType, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        args.add(conceptId);
        StringBuilder sql = new StringBuilder("""
                select c.concept_id, c.dataset_id, c.provider, c.concept_type,
                       c.external_code, c.parent_code, c.preferred_label, c.description,
                       c.level_value, c.category_path, c.normalized_label, c.raw_json::text as raw_json
                  from tb_skill_dataset_relation r
                  join tb_skill_dataset_concept c
                    on c.dataset_id = r.dataset_id
                   and c.concept_id = r.target_concept_id
                 where r.dataset_id = ?
                   and r.source_concept_id = ?
                """);
        if (relationType != null) {
            sql.append("   and r.relation_type = ?\n");
            args.add(relationType);
        }
        sql.append(" order by c.concept_type, c.preferred_label\n limit ?\n");
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), conceptMapper(), args.toArray());
    }

    @Override
    public Page<SkillConcept> findChildConcepts(
            String datasetId,
            String conceptId,
            String relationType,
            Pageable pageable) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        args.add(conceptId);
        StringBuilder where = new StringBuilder("""
                 where r.dataset_id = ?
                   and r.source_concept_id = ?
                """);
        if (relationType != null) {
            where.append("   and r.relation_type = ?\n");
            args.add(relationType);
        }

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(pageable.getPageSize());
        queryArgs.add(pageable.getOffset());
        List<SkillConcept> content = jdbcTemplate.query("""
                select c.concept_id, c.dataset_id, c.provider, c.concept_type,
                       c.external_code, c.parent_code, c.preferred_label, c.description,
                       c.level_value, c.category_path, c.normalized_label, c.raw_json::text as raw_json
                  from tb_skill_dataset_relation r
                  join tb_skill_dataset_concept c
                    on c.dataset_id = r.dataset_id
                   and c.concept_id = r.target_concept_id
                """ + where + orderBy(pageable.getSort(), childConceptSortColumns(), "c.concept_id desc") + """
                 limit ? offset ?
                """, conceptMapper(), queryArgs.toArray());
        Long total = jdbcTemplate.queryForObject("""
                select count(*)
                  from tb_skill_dataset_relation r
                  join tb_skill_dataset_concept c
                    on c.dataset_id = r.dataset_id
                   and c.concept_id = r.target_concept_id
                """ + where, Long.class, args.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SkillConcept> findConceptsByIds(String datasetId, List<String> conceptIds) {
        if (conceptIds == null || conceptIds.isEmpty()) {
            return List.of();
        }
        List<String> uniqueIds = conceptIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", uniqueIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        args.addAll(uniqueIds);

        List<SkillConcept> concepts = jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where dataset_id = ?
                   and concept_id in (
                """ + placeholders + """
                   )
                """, conceptMapper(), args.toArray());
        Map<String, SkillConcept> byId = new LinkedHashMap<>();
        concepts.forEach(concept -> byId.put(concept.conceptId(), concept));
        return uniqueIds.stream()
                .map(byId::get)
                .filter(concept -> concept != null)
                .toList();
    }

    @Override
    public List<SkillRelation> findRelations(String datasetId, String relationType, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        StringBuilder sql = new StringBuilder("""
                select relation_id, dataset_id, provider,
                       source_concept_id, target_concept_id,
                       relation_type, confidence, raw_json::text as raw_json
                  from tb_skill_dataset_relation
                 where dataset_id = ?
                """);
        if (relationType != null) {
            sql.append("   and relation_type = ?\n");
            args.add(relationType);
        }
        sql.append(" order by relation_type, relation_id\n limit ?\n");
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), relationMapper(), args.toArray());
    }

    @Override
    public List<SkillRelation> findOutgoingRelations(String datasetId, String sourceConceptId, String relationType, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(datasetId);
        args.add(sourceConceptId);
        StringBuilder sql = new StringBuilder("""
                select relation_id, dataset_id, provider,
                       source_concept_id, target_concept_id,
                       relation_type, confidence, raw_json::text as raw_json
                  from tb_skill_dataset_relation
                 where dataset_id = ?
                   and source_concept_id = ?
                """);
        if (relationType != null) {
            sql.append("   and relation_type = ?\n");
            args.add(relationType);
        }
        sql.append(" order by relation_type, relation_id\n limit ?\n");
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), relationMapper(), args.toArray());
    }

    @Override
    public long countConcepts(String datasetId, String provider, String conceptType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select count(*)
                  from tb_skill_dataset_concept
                 where dataset_id = ?
                """);
        args.add(datasetId);
        if (provider != null) {
            sql.append("   and provider = ?\n");
            args.add(provider);
        }
        if (conceptType != null) {
            sql.append("   and concept_type = ?\n");
            args.add(conceptType);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    @Override
    public List<SkillConcept> findConceptsForEmbedding(
            String datasetId,
            String provider,
            String conceptType,
            int limit,
            long offset) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_dataset_concept
                 where dataset_id = ?
                """);
        args.add(datasetId);
        if (provider != null) {
            sql.append("   and provider = ?\n");
            args.add(provider);
        }
        if (conceptType != null) {
            sql.append("   and concept_type = ?\n");
            args.add(conceptType);
        }
        sql.append(" order by concept_id\n limit ? offset ?\n");
        args.add(normalizeLimit(limit));
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), conceptMapper(), args.toArray());
    }

    @Override
    public boolean conceptEmbeddingExists(
            String conceptId,
            String embeddingProvider,
            String embeddingModel,
            String textType,
            String sourceHash) {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                  from tb_skill_dataset_concept_embedding
                 where concept_id = ?
                   and embedding_provider = ?
                   and embedding_model = ?
                   and text_type = ?
                   and source_hash = ?
                """, Long.class, conceptId, embeddingProvider, embeddingModel, textType, sourceHash);
        return count != null && count > 0;
    }

    @Override
    public void deleteConceptEmbedding(
            String conceptId,
            String embeddingProvider,
            String embeddingModel,
            String textType) {
        jdbcTemplate.update("""
                delete from tb_skill_dataset_concept_embedding
                 where concept_id = ?
                   and embedding_provider = ?
                   and embedding_model = ?
                   and text_type = ?
                """, conceptId, embeddingProvider, embeddingModel, textType);
    }

    @Override
    public void upsertConceptEmbedding(SkillConceptEmbedding embedding) {
        jdbcTemplate.update("""
                insert into tb_skill_dataset_concept_embedding (
                    embedding_id, concept_id, dataset_id, provider, concept_type,
                    external_code, preferred_label,
                    embedding_provider, embedding_model, embedding_dim,
                    text_type, source_text, source_hash, embedding,
                    status, created_at, updated_at
                ) values (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as vector),
                    'COMPLETED', current_timestamp, current_timestamp
                )
                on conflict (concept_id, embedding_provider, embedding_model, text_type, source_hash)
                do update set
                    dataset_id = excluded.dataset_id,
                    provider = excluded.provider,
                    concept_type = excluded.concept_type,
                    external_code = excluded.external_code,
                    preferred_label = excluded.preferred_label,
                    embedding_dim = excluded.embedding_dim,
                    source_text = excluded.source_text,
                    embedding = excluded.embedding,
                    status = 'COMPLETED',
                    error_message = null,
                    updated_at = current_timestamp
                """,
                embedding.embeddingId(),
                embedding.conceptId(),
                embedding.datasetId(),
                embedding.provider(),
                embedding.conceptType(),
                embedding.externalCode(),
                embedding.preferredLabel(),
                embedding.embeddingProvider(),
                embedding.embeddingModel(),
                embedding.embeddingDim(),
                embedding.textType(),
                embedding.sourceText(),
                embedding.sourceHash(),
                vectorLiteral(embedding.embedding()));
    }

    @Override
    public List<SkillConceptVectorSearchHit> vectorSearchConcepts(
            String datasetId,
            String provider,
            String conceptType,
            String embeddingProvider,
            String embeddingModel,
            String textType,
            List<Double> queryVector,
            String categoryPathPrefix,
            String levelValue,
            int limit,
            double minScore) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select e.embedding_id,
                       e.embedding_provider,
                       e.embedding_model,
                       e.text_type,
                       e.source_text,
                       1 - (e.embedding <=> cast(? as vector)) as score,
                       c.concept_id, c.dataset_id, c.provider, c.concept_type,
                       c.external_code, c.parent_code, c.preferred_label, c.description,
                       c.level_value, c.category_path, c.normalized_label, c.raw_json::text as raw_json
                  from tb_skill_dataset_concept_embedding e
                  join tb_skill_dataset_concept c
                    on c.concept_id = e.concept_id
                 where e.dataset_id = ?
                   and e.embedding_provider = ?
                   and e.embedding_model = ?
                   and e.text_type = ?
                """);
        args.add(vectorLiteral(queryVector));
        args.add(datasetId);
        args.add(embeddingProvider);
        args.add(embeddingModel);
        args.add(textType);
        if (provider != null) {
            sql.append("   and e.provider = ?\n");
            args.add(provider);
        }
        if (conceptType != null) {
            sql.append("   and e.concept_type = ?\n");
            args.add(conceptType);
        }
        if (categoryPathPrefix != null) {
            sql.append("   and c.category_path like ?\n");
            args.add(categoryPathPrefix + "%");
        }
        if (levelValue != null) {
            sql.append("   and c.level_value = ?\n");
            args.add(levelValue);
        }
        sql.append("""
                   and 1 - (e.embedding <=> cast(? as vector)) >= ?
                 order by e.embedding <=> cast(? as vector)
                 limit ?
                """);
        args.add(vectorLiteral(queryVector));
        args.add(minScore);
        args.add(vectorLiteral(queryVector));
        args.add(normalizeLimit(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SkillConceptVectorSearchHit(
                conceptMapper().mapRow(rs, rowNum),
                rs.getString("embedding_id"),
                rs.getString("embedding_provider"),
                rs.getString("embedding_model"),
                rs.getString("text_type"),
                rs.getDouble("score"),
                rs.getString("source_text")), args.toArray());
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 100 : Math.min(limit, 1000);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.trim() + "%";
    }

    private String orderBy(Sort sort, Map<String, String> allowedColumns, String defaultOrder) {
        return " order by " + orderByExpressions(sort, allowedColumns, defaultOrder) + "\n";
    }

    private String orderByExpressions(Sort sort, Map<String, String> allowedColumns, String defaultOrder) {
        if (sort == null || sort.isUnsorted()) {
            return defaultOrder;
        }
        List<String> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = allowedColumns.get(order.getProperty());
            if (column != null) {
                orders.add(column + (order.isAscending() ? " asc" : " desc"));
            }
        }
        return orders.isEmpty() ? defaultOrder : String.join(", ", orders);
    }

    private Map<String, String> datasetSortColumns() {
        return Map.of(
                "datasetId", "dataset_id",
                "provider", "provider",
                "datasetName", "dataset_name",
                "version", "version",
                "language", "language",
                "importedAt", "imported_at");
    }

    private Map<String, String> conceptSortColumns() {
        return Map.of(
                "conceptId", "concept_id",
                "datasetId", "dataset_id",
                "conceptType", "concept_type",
                "externalCode", "external_code",
                "preferredLabel", "preferred_label",
                "levelValue", "level_value",
                "normalizedLabel", "normalized_label");
    }

    private Map<String, String> childConceptSortColumns() {
        return Map.of(
                "conceptId", "c.concept_id",
                "datasetId", "c.dataset_id",
                "conceptType", "c.concept_type",
                "externalCode", "c.external_code",
                "preferredLabel", "c.preferred_label",
                "levelValue", "c.level_value",
                "normalizedLabel", "c.normalized_label");
    }

    private RowMapper<SkillDataset> datasetMapper() {
        return (rs, rowNum) -> new SkillDataset(
                rs.getString("dataset_id"),
                rs.getString("provider"),
                rs.getString("dataset_name"),
                rs.getString("version"),
                rs.getString("language"),
                rs.getString("source_location"),
                rs.getTimestamp("imported_at").toInstant());
    }

    private RowMapper<SkillConcept> conceptMapper() {
        return (rs, rowNum) -> new SkillConcept(
                rs.getString("concept_id"),
                rs.getString("dataset_id"),
                rs.getString("provider"),
                rs.getString("concept_type"),
                rs.getString("external_code"),
                rs.getString("parent_code"),
                rs.getString("preferred_label"),
                rs.getString("description"),
                rs.getString("level_value"),
                rs.getString("category_path"),
                rs.getString("normalized_label"),
                rs.getString("raw_json"));
    }

    private RowMapper<SkillRelation> relationMapper() {
        return (rs, rowNum) -> new SkillRelation(
                rs.getString("relation_id"),
                rs.getString("dataset_id"),
                rs.getString("provider"),
                rs.getString("source_concept_id"),
                rs.getString("target_concept_id"),
                rs.getString("relation_type"),
                getDouble(rs, "confidence"),
                rs.getString("raw_json"));
    }

    private Double getDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding == null ? "[]" : embedding.toString();
    }
}

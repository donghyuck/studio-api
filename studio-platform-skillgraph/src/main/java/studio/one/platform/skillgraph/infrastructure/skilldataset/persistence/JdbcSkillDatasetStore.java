package studio.one.platform.skillgraph.infrastructure.skilldataset.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
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
    public List<SkillConcept> findConcepts(String datasetId, String conceptType, int limit) {
        return jdbcTemplate.query("""
                select concept_id, dataset_id, provider, concept_type,
                       external_code, parent_code, preferred_label, description,
                       level_value, category_path, normalized_label, raw_json::text as raw_json
                  from tb_skill_concept
                 where dataset_id = ?
                   and (? is null or concept_type = ?)
                 order by concept_type, preferred_label
                 limit ?
                """, conceptMapper(), datasetId, conceptType, conceptType, normalizeLimit(limit));
    }

    @Override
    public List<SkillRelation> findRelations(String datasetId, String relationType, int limit) {
        return jdbcTemplate.query("""
                select relation_id, dataset_id, provider,
                       source_concept_id, target_concept_id,
                       relation_type, confidence, raw_json::text as raw_json
                  from tb_skill_dataset_relation
                 where dataset_id = ?
                   and (? is null or relation_type = ?)
                 order by relation_type, relation_id
                 limit ?
                """, relationMapper(), datasetId, relationType, relationType, normalizeLimit(limit));
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 100 : Math.min(limit, 1000);
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
}
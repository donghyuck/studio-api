package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;
import studio.one.platform.skillgraph.domain.port.SkillMappingStore;

@RequiredArgsConstructor
public class JdbcSkillMappingStore implements SkillMappingStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public NcsSkillMapping saveNcsMapping(NcsSkillMapping mapping) {
        int updated = template.update("""
                UPDATE tb_skill_ncs_mapping
                SET ncs_unit_id = :ncsUnitId,
                    skill_id = :skillId,
                    weight = :weight
                WHERE mapping_id = :mappingId
                """, ncsParams(mapping));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_ncs_mapping(mapping_id, ncs_unit_id, skill_id, weight, created_at)
                    VALUES(:mappingId, :ncsUnitId, :skillId, :weight, :createdAt)
                    """, ncsParams(mapping));
        }
        return mapping;
    }

    @Override
    public CourseSkillMapping saveCourseMapping(CourseSkillMapping mapping) {
        int updated = template.update("""
                UPDATE tb_skill_course_mapping
                SET course_id = :courseId,
                    skill_id = :skillId,
                    weight = :weight
                WHERE mapping_id = :mappingId
                """, courseParams(mapping));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_course_mapping(mapping_id, course_id, skill_id, weight, created_at)
                    VALUES(:mappingId, :courseId, :skillId, :weight, :createdAt)
                    """, courseParams(mapping));
        }
        return mapping;
    }

    @Override
    public List<NcsSkillMapping> findNcsMappings(String ncsUnitId) {
        return template.query("""
                SELECT * FROM tb_skill_ncs_mapping
                WHERE (:ncsUnitId IS NULL OR ncs_unit_id = :ncsUnitId)
                ORDER BY created_at DESC
                """, new MapSqlParameterSource("ncsUnitId", normalize(ncsUnitId)), this::mapNcs);
    }

    @Override
    public List<CourseSkillMapping> findCourseMappings(String courseId) {
        return template.query("""
                SELECT * FROM tb_skill_course_mapping
                WHERE (:courseId IS NULL OR course_id = :courseId)
                ORDER BY created_at DESC
                """, new MapSqlParameterSource("courseId", normalize(courseId)), this::mapCourse);
    }

    @Override
    public List<CourseSkillMapping> findCoursesBySkillIds(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return template.query("""
                SELECT * FROM tb_skill_course_mapping
                WHERE skill_id IN (:skillIds)
                """, new MapSqlParameterSource("skillIds", skillIds), this::mapCourse);
    }

    private MapSqlParameterSource ncsParams(NcsSkillMapping mapping) {
        return new MapSqlParameterSource()
                .addValue("mappingId", mapping.mappingId())
                .addValue("ncsUnitId", mapping.ncsUnitId())
                .addValue("skillId", mapping.skillId())
                .addValue("weight", mapping.weight())
                .addValue("createdAt", Timestamp.from(mapping.createdAt()));
    }

    private MapSqlParameterSource courseParams(CourseSkillMapping mapping) {
        return new MapSqlParameterSource()
                .addValue("mappingId", mapping.mappingId())
                .addValue("courseId", mapping.courseId())
                .addValue("skillId", mapping.skillId())
                .addValue("weight", mapping.weight())
                .addValue("createdAt", Timestamp.from(mapping.createdAt()));
    }

    private NcsSkillMapping mapNcs(ResultSet rs, int rowNum) throws SQLException {
        return new NcsSkillMapping(
                rs.getString("mapping_id"),
                rs.getString("ncs_unit_id"),
                rs.getString("skill_id"),
                rs.getDouble("weight"),
                instant(rs.getTimestamp("created_at")));
    }

    private CourseSkillMapping mapCourse(ResultSet rs, int rowNum) throws SQLException {
        return new CourseSkillMapping(
                rs.getString("mapping_id"),
                rs.getString("course_id"),
                rs.getString("skill_id"),
                rs.getDouble("weight"),
                instant(rs.getTimestamp("created_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

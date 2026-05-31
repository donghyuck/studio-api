package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.NcsSkillMapping;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.model.SkillRelation;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillGraphStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillMappingStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillProjectionStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillTaxonomyStore;

class JdbcSkillGraphStoreTest {

    private EmbeddedDatabase database;
    private JdbcSkillCandidateStore candidateStore;
    private JdbcSkillDictionaryStore dictionaryStore;
    private JdbcSkillTaxonomyStore taxonomyStore;
    private JdbcSkillGraphStore graphStore;
    private JdbcSkillMappingStore mappingStore;
    private JdbcSkillProjectionStore projectionStore;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(database);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_source_chunk (
                    source_chunk_id VARCHAR(100) PRIMARY KEY,
                    source_type VARCHAR(100),
                    source_id VARCHAR(200),
                    chunk_id VARCHAR(200),
                    text CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_candidate (
                    candidate_id VARCHAR(100) PRIMARY KEY,
                    source_chunk_id VARCHAR(100),
                    source_type VARCHAR(100),
                    source_id VARCHAR(200),
                    term VARCHAR(300) NOT NULL,
                    normalized_term VARCHAR(300) NOT NULL,
                    search_text CLOB,
                    skill_type VARCHAR(40),
                    action VARCHAR(200),
                    technology CLOB,
                    target CLOB,
                    evidence_text CLOB,
                    context CLOB,
                    difficulty VARCHAR(40),
                    extraction_method VARCHAR(80),
                    confidence_detail CLOB,
                    source_position CLOB,
                    normalization_info CLOB,
                    mapping_candidates CLOB,
                    review_status VARCHAR(40),
                    feedback CLOB,
                    status VARCHAR(40) NOT NULL,
                    confidence DOUBLE NOT NULL,
                    occurrence_count INT NOT NULL,
                    matched_skill_id VARCHAR(100),
                    reviewer_note CLOB,
                    embedding CLOB,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_dictionary (
                    skill_id VARCHAR(100) PRIMARY KEY,
                    name VARCHAR(300) NOT NULL,
                    normalized_name VARCHAR(300) NOT NULL,
                    category_id VARCHAR(100),
                    status VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_alias (
                    alias_id VARCHAR(100) PRIMARY KEY,
                    skill_id VARCHAR(100) NOT NULL,
                    alias VARCHAR(300) NOT NULL,
                    normalized_alias VARCHAR(300) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_category (
                    category_id VARCHAR(100) PRIMARY KEY,
                    parent_category_id VARCHAR(100),
                    name VARCHAR(200) NOT NULL,
                    display_order INT NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_relation (
                    relation_id VARCHAR(100) PRIMARY KEY,
                    source_skill_id VARCHAR(100) NOT NULL,
                    target_skill_id VARCHAR(100) NOT NULL,
                    relation_type VARCHAR(40) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_ncs_mapping (
                    mapping_id VARCHAR(100) PRIMARY KEY,
                    ncs_unit_id VARCHAR(100) NOT NULL,
                    skill_id VARCHAR(100) NOT NULL,
                    weight DOUBLE NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_course_mapping (
                    mapping_id VARCHAR(100) PRIMARY KEY,
                    course_id VARCHAR(100) NOT NULL,
                    skill_id VARCHAR(100) NOT NULL,
                    weight DOUBLE NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_projection (
                    projection_id VARCHAR(100) NOT NULL,
                    skill_id VARCHAR(100) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    cluster_id VARCHAR(100),
                    reduction_algorithm VARCHAR(30),
                    clustering_algorithm VARCHAR(30),
                    embedding_provider VARCHAR(100),
                    embedding_model VARCHAR(200),
                    embedding_dimension INT,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (projection_id, skill_id)
                )
                """);
        template.getJdbcTemplate().execute("""
                CREATE TABLE tb_skill_cluster (
                    cluster_id VARCHAR(100) PRIMARY KEY,
                    label VARCHAR(200),
                    algorithm VARCHAR(100) NOT NULL,
                    item_count INT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        candidateStore = new JdbcSkillCandidateStore(template);
        dictionaryStore = new JdbcSkillDictionaryStore(template);
        taxonomyStore = new JdbcSkillTaxonomyStore(template);
        graphStore = new JdbcSkillGraphStore(template);
        mappingStore = new JdbcSkillMappingStore(template);
        projectionStore = new JdbcSkillProjectionStore(template);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void candidateStorePersistsAndUpdatesCandidate() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        candidateStore.saveSourceChunk(new SkillSourceChunk("source-1", "course", "course-1", "chunk-1", "JWT", now));
        candidateStore.saveCandidate(new SkillCandidate("candidate-1", "source-1", "course", "course-1",
                "JWT", "jwt", SkillCandidateStatus.PENDING, 0.7d, 1, null, null, now, now));

        candidateStore.saveCandidate(new SkillCandidate("candidate-1", "source-1", "course", "course-1",
                "JWT", "jwt", SkillCandidateStatus.NOISE, 0.7d, 2, null, "duplicate", now, now.plusSeconds(1)));

        var candidate = candidateStore.findCandidate("candidate-1").orElseThrow();
        assertEquals(SkillCandidateStatus.NOISE, candidate.status());
        assertEquals(2, candidate.occurrenceCount());
        assertEquals("duplicate", candidate.reviewerNote());
        assertTrue(candidateStore.findCandidateByNormalizedTerm("jwt").isPresent());
    }

    @Test
    void candidateStoreSearchesWithOptionalFilters() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        candidateStore.saveSourceChunk(new SkillSourceChunk("source-1", "course", "course-1", "chunk-1", "Spring", now));
        candidateStore.saveCandidate(new SkillCandidate("candidate-1", "source-1", "course", "course-1",
                "Spring Boot", "spring boot", "Spring Boot REST API 구현", "TECH_SKILL", "구현",
                List.of("Spring Boot"), "REST API", "Spring Boot API", "backend", "INTERMEDIATE",
                "test", null, null, null, null, null, null,
                false,
                SkillCandidateStatus.PENDING, 0.9d, 1, null, null, now, now));

        var all = candidateStore.searchCandidates(null, null, null, null, 10);
        var filtered = candidateStore.searchCandidates(SkillCandidateStatus.PENDING, "spring", "course", "course-1", 10);
        var missing = candidateStore.searchCandidates(SkillCandidateStatus.APPROVED, "spring", "course", "course-1", 10);

        assertEquals(1, all.size());
        assertEquals("candidate-1", filtered.get(0).candidateId());
        assertEquals("Spring Boot REST API 구현", filtered.get(0).searchText());
        assertEquals(List.of("Spring Boot"), filtered.get(0).technology());
        assertTrue(missing.isEmpty());
    }

    @Test
    void dictionaryStorePersistsAndSearchesSkill() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", "backend", "ACTIVE", now, now));

        var skill = dictionaryStore.findByNormalizedName("spring boot").orElseThrow();
        assertEquals("skill-1", skill.skillId());
        assertEquals(1, dictionaryStore.search("spring", 10).size());
    }

    @Test
    void dictionaryStoreSearchesWithFiltersAndSort() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        dictionaryStore.save(new SkillDictionary("skill-1", "Alpha API", "alpha api", "backend", "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-2", "Zulu API", "zulu api", "backend", "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-3", "Beta API", "beta api", "frontend", "ACTIVE", now, now));
        dictionaryStore.save(new SkillDictionary("skill-4", "Legacy API", "legacy api", "backend", "INACTIVE", now, now));

        var page = dictionaryStore.search(
                "api",
                "ACTIVE",
                "backend",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "name")));

        assertEquals(2, page.getTotalElements());
        assertEquals(List.of("Zulu API", "Alpha API"), page.getContent().stream()
                .map(SkillDictionary::name)
                .toList());
    }

    @Test
    void dictionaryStorePersistsAndFindsAlias() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", "backend", "ACTIVE", now, now));
        dictionaryStore.saveAlias(new SkillAlias("alias-1", "skill-1", "스프링부트", "스프링부트", now));

        var skill = dictionaryStore.findByNormalizedAlias("스프링부트").orElseThrow();
        assertEquals("skill-1", skill.skillId());
    }

    @Test
    void taxonomyStoreSearchesRootAndChildCategories() {
        taxonomyStore.saveCategory(new SkillCategory("backend", null, "Backend", 1));
        taxonomyStore.saveCategory(new SkillCategory("java", "backend", "Java", 1));

        var roots = taxonomyStore.findCategories(null);
        var children = taxonomyStore.findCategories("backend");

        assertEquals(1, roots.size());
        assertEquals("backend", roots.get(0).categoryId());
        assertEquals(1, children.size());
        assertEquals("java", children.get(0).categoryId());
    }

    @Test
    void graphStoreSearchesRelationsWithOptionalFilters() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        graphStore.saveRelation(new SkillRelation("relation-1", "spring", "java",
                SkillRelationType.PREREQUISITE, now));

        var all = graphStore.findRelations(null, null);
        var bySkill = graphStore.findRelations("spring", null);
        var byType = graphStore.findRelations(null, SkillRelationType.PREREQUISITE);
        var missing = graphStore.findRelations("python", SkillRelationType.PREREQUISITE);

        assertEquals(1, all.size());
        assertEquals("relation-1", bySkill.get(0).relationId());
        assertEquals("relation-1", byType.get(0).relationId());
        assertTrue(missing.isEmpty());
    }

    @Test
    void mappingStoreSearchesWithOptionalFilters() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        mappingStore.saveNcsMapping(new NcsSkillMapping("ncs-1", "unit-1", "spring", 0.8d, now));
        mappingStore.saveCourseMapping(new CourseSkillMapping("course-1", "course-a", "spring", 0.7d, now));

        var allNcs = mappingStore.findNcsMappings(null);
        var ncsByUnit = mappingStore.findNcsMappings("unit-1");
        var allCourses = mappingStore.findCourseMappings(null);
        var coursesById = mappingStore.findCourseMappings("course-a");

        assertEquals(1, allNcs.size());
        assertEquals("ncs-1", ncsByUnit.get(0).mappingId());
        assertEquals(1, allCourses.size());
        assertEquals("course-1", coursesById.get(0).mappingId());
    }

    @Test
    void projectionStoreSearchesWithOptionalClusterFilter() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        projectionStore.replaceProjection("default", List.of(
                new SkillProjection("default", "spring", 1.0d, 2.0d, "cluster-a", 0, now),
                new SkillProjection("default", "java", 3.0d, 4.0d, "cluster-b", 0, now)),
                List.of(new SkillCluster("cluster-a", "Backend", "manual", 1, now)));

        var all = projectionStore.findProjectionPoints("default", null, PageRequest.of(0, 10));
        var byCluster = projectionStore.findProjectionPoints("default", "cluster-a", PageRequest.of(0, 10));
        var summaries = projectionStore.listProjections(PageRequest.of(0, 10));

        assertEquals(2, all.getContent().size());
        assertEquals(1, byCluster.getContent().size());
        assertEquals("spring", byCluster.getContent().get(0).skillId());
        assertEquals(1, summaries.getContent().size());
        assertEquals("default", summaries.getContent().get(0).projectionId());
        assertEquals(2, summaries.getContent().get(0).itemCount());
        assertEquals(2, summaries.getContent().get(0).clusterCount());
        assertEquals("manual", summaries.getContent().get(0).algorithm());
    }
}

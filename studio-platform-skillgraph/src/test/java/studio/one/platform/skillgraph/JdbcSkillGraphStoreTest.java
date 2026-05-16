package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.jdbc.JdbcSkillDictionaryStore;

class JdbcSkillGraphStoreTest {

    private EmbeddedDatabase database;
    private JdbcSkillCandidateStore candidateStore;
    private JdbcSkillDictionaryStore dictionaryStore;

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
                    status VARCHAR(40) NOT NULL,
                    confidence DOUBLE NOT NULL,
                    occurrence_count INT NOT NULL,
                    matched_skill_id VARCHAR(100),
                    reviewer_note CLOB,
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
        candidateStore = new JdbcSkillCandidateStore(template);
        dictionaryStore = new JdbcSkillDictionaryStore(template);
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
                "Spring Boot", "spring boot", SkillCandidateStatus.PENDING, 0.9d, 1, null, null, now, now));

        var all = candidateStore.searchCandidates(null, null, null, null, 10);
        var filtered = candidateStore.searchCandidates(SkillCandidateStatus.PENDING, "spring", "course", "course-1", 10);
        var missing = candidateStore.searchCandidates(SkillCandidateStatus.APPROVED, "spring", "course", "course-1", 10);

        assertEquals(1, all.size());
        assertEquals("candidate-1", filtered.get(0).candidateId());
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
    void dictionaryStorePersistsAndFindsAlias() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", "backend", "ACTIVE", now, now));
        dictionaryStore.saveAlias(new SkillAlias("alias-1", "skill-1", "스프링부트", "스프링부트", now));

        var skill = dictionaryStore.findByNormalizedAlias("스프링부트").orElseThrow();
        assertEquals("skill-1", skill.skillId());
    }
}

package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillCandidateReviewService;
import studio.one.platform.skillgraph.application.service.RegexSkillCandidateExtractor;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;

class DefaultSkillExtractionServiceTest {

    @Test
    void extractsAndStoresCandidatesFromText() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(store, new PatternSkillCandidateExtractor());

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "Spring Boot와 OAuth2 인증 기술"));

        assertFalse(result.candidates().isEmpty());
        assertEquals(result.extractedCount(), store.searchCandidates(null, null, 100).size());
    }

    @Test
    void dryRunExtractsCandidatesWithoutStoringSourceChunkOrCandidate() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(store, new PatternSkillCandidateExtractor());

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", "chunk-1",
                "Spring Security와 JPA 기술"));

        assertFalse(result.candidates().isEmpty());
        assertEquals(0, store.sourceChunks().size());
        assertEquals(0, store.searchCandidates(null, null, 100).size());
        assertEquals(null, result.sourceChunkId());
        assertFalse(result.candidates().stream()
                .filter(candidate -> candidate.normalizedTerm().contains("spring"))
                .toList()
                .isEmpty());
    }

    @Test
    void dryRunAppliesDictionaryMatchWithoutStoringCandidate() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", null, "Spring Boot"));

        assertEquals(SkillCandidateStatus.MATCHED, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
        assertEquals(0, candidateStore.searchCandidates(null, null, 100).size());
    }

    @Test
    void reviewUpdatesCandidateStatus() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor extractionService = new RegexSkillCandidateExtractor(store,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(store);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "JWT 기술"));
        String candidateId = result.candidates().get(0).candidateId();

        var reviewed = reviewService.review(candidateId,
                new SkillCandidateReviewCommand(SkillCandidateStatus.NOISE, null, "not a skill"));

        assertEquals(SkillCandidateStatus.NOISE, reviewed.status());
        assertEquals("not a skill", reviewed.reviewerNote());
    }

    @Test
    void duplicateNormalizedTermIncrementsOccurrenceWithinSameSourceChunk() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(store, new PatternSkillCandidateExtractor());

        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Spring Boot"));
        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-2", "Spring Boot"));

        var candidates = store.searchCandidates(null, "spring boot", 10);
        assertEquals(2, candidates.size());
        assertEquals(1, candidates.get(0).occurrenceCount());
        assertEquals(1, candidates.get(1).occurrenceCount());
    }

    @Test
    void duplicateNormalizedTermIncrementsOccurrenceWithinSameChunk() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(store, new PatternSkillCandidateExtractor());

        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));
        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var candidates = store.searchCandidates(null, "kubernetes", 10);
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).occurrenceCount());
    }

    @Test
    void rejectsOversizedExtractionText() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(store, new PatternSkillCandidateExtractor());

        assertThrows(IllegalArgumentException.class,
                () -> service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "x".repeat(200_001))));
    }

    @Test
    void exactDictionaryMatchMarksCandidateAsMatched() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Spring Boot"));

        assertEquals(SkillCandidateStatus.MATCHED, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
    }

    @Test
    void aliasDictionaryMatchMarksCandidateAsMatched() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        dictionaryStore.saveAlias(new SkillAlias("alias-1", "skill-1", "스프링부트", "스프링부트"));
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "스프링부트 기술"));

        assertEquals(SkillCandidateStatus.MATCHED, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
    }

    @Test
    void embeddingSimilarityMarksAliasCandidate() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(1.0d, 0.0d));
        RegexSkillCandidateExtractor service = new RegexSkillCandidateExtractor(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(0.9d, 0.4d),
                new SkillMatchPolicy(0.98d, 0.80d));

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "SpringBoot"));

        assertEquals(SkillCandidateStatus.ALIAS_CANDIDATE, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
    }

    @Test
    void approvingNewCandidateAddsDictionaryEntry() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        RegexSkillCandidateExtractor extractionService = new RegexSkillCandidateExtractor(candidateStore,
                dictionaryStore, new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var reviewed = reviewService.review(result.candidates().get(0).candidateId(),
                new SkillCandidateReviewCommand(SkillCandidateStatus.APPROVED, null, "new skill"));

        assertFalse(dictionaryStore.findByNormalizedName("kubernetes").isEmpty());
        assertFalse(reviewed.matchedSkillId().isBlank());
    }

    @Test
    void acceptingAliasCandidateAddsAlias() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        RegexSkillCandidateExtractor extractionService = new RegexSkillCandidateExtractor(candidateStore,
                dictionaryStore, new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "SpringBoot"));

        reviewService.review(result.candidates().get(0).candidateId(),
                new SkillCandidateReviewCommand(SkillCandidateStatus.ALIAS_CANDIDATE, "skill-1", "alias"));

        assertFalse(dictionaryStore.findByNormalizedAlias("springboot").isEmpty());
    }
}

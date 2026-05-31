package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.SkillCandidateAutoApproveCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateBulkReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateRecommendationJobCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillRecommendationApplyCommand;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillCandidateReviewService;
import studio.one.platform.skillgraph.application.service.DefaultSkillCandidateRecommendationService;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillRecommendationStore;

class DefaultSkillExtractionServiceTest {

    @Test
    void extractsAndStoresCandidatesFromText() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor());

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "Spring Boot와 OAuth2 인증 기술"));

        assertFalse(result.candidates().isEmpty());
        assertEquals(result.extractedCount(), store.searchCandidates(null, null, 100).size());
    }

    @Test
    void dryRunExtractsCandidatesWithoutStoringSourceChunkOrCandidate() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor());

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
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", null, "Spring Boot"));

        assertEquals(SkillCandidateStatus.MATCHED, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
        assertEquals(0, candidateStore.searchCandidates(null, null, 100).size());
    }

    @Test
    void reviewUpdatesCandidateStatus() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(store,
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
    void bulkReviewUpdatesSelectedCandidates() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(store,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(store);
        var first = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "JWT 기술"));
        var second = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-2", "OAuth2 기술"));

        var reviewed = reviewService.reviewAll(new SkillCandidateBulkReviewCommand(
                List.of(first.candidates().get(0).candidateId(), second.candidates().get(0).candidateId(),
                        first.candidates().get(0).candidateId()),
                SkillCandidateStatus.NOISE,
                false,
                "noise"));

        assertEquals(2, reviewed.size());
        assertEquals(SkillCandidateStatus.NOISE, reviewed.get(0).status());
        assertEquals(SkillCandidateStatus.NOISE, reviewed.get(1).status());
        assertEquals("noise", reviewed.get(0).reviewerNote());
    }

    @Test
    void bulkReviewRejectsUnsupportedStatus() {
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(
                new InMemorySkillCandidateStore());

        assertThrows(IllegalArgumentException.class,
                () -> reviewService.reviewAll(new SkillCandidateBulkReviewCommand(
                        List.of("candidate-1"),
                        SkillCandidateStatus.MATCHED,
                        false,
                        null)));
    }

    @Test
    void bulkApproveCanGenerateDictionaryEmbedding() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore, text -> List.of(0.1d, 0.9d));
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var reviewed = reviewService.reviewAll(new SkillCandidateBulkReviewCommand(
                List.of(result.candidates().get(0).candidateId()),
                SkillCandidateStatus.APPROVED,
                true,
                "approved with embedding"));

        assertEquals(1, reviewed.size());
        assertEquals(SkillCandidateStatus.APPROVED, reviewed.get(0).status());
        assertEquals(1, dictionaryStore.findVectorItems(10).size());
        assertEquals(reviewed.get(0).matchedSkillId(), dictionaryStore.findVectorItems(10).get(0).skillId());
    }

    @Test
    void autoApproveWithZeroSimilarityThresholdIncludesCandidateWithoutSimilarSkill() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var approved = reviewService.autoApprove(new SkillCandidateAutoApproveCommand(
                List.of(result.candidates().get(0).candidateId()),
                0.6d,
                0.0d,
                false,
                "auto approved"));

        assertEquals(1, approved.approvedCount());
        assertEquals(0, approved.skippedCount());
        assertEquals(SkillCandidateStatus.APPROVED, approved.approved().get(0).status());
        assertFalse(dictionaryStore.findByNormalizedName("kubernetes").isEmpty());
    }

    @Test
    void autoApproveWithPositiveSimilarityThresholdSkipsCandidateWithoutSimilarSkill() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var approved = reviewService.autoApprove(new SkillCandidateAutoApproveCommand(
                List.of(result.candidates().get(0).candidateId()),
                0.6d,
                0.8d,
                false,
                "auto approved"));

        assertEquals(0, approved.approvedCount());
        assertEquals(1, approved.skippedCount());
        assertEquals("similar skill is missing", approved.skipped().get(0).reason());
    }

    @Test
    void autoApprovePreservesMatchedDictionarySkillWhenSimilarityPasses() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(1.0d, 0.0d));
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                dictionaryStore, new PatternSkillCandidateExtractor(), text -> List.of(0.9d, 0.4d),
                new SkillMatchPolicy(0.98d, 0.80d));
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "SpringBoot"));

        var approved = reviewService.autoApprove(new SkillCandidateAutoApproveCommand(
                List.of(result.candidates().get(0).candidateId()),
                0.6d,
                0.9d,
                false,
                "auto approved"));

        assertEquals(1, approved.approvedCount());
        assertEquals(SkillCandidateStatus.APPROVED, approved.approved().get(0).status());
        assertEquals("skill-1", approved.approved().get(0).matchedSkillId());
        assertEquals("skill-1", approved.approved().get(0).matchedSkill().skillId());
        assertEquals(0.9138115486202572d, approved.approved().get(0).matchedSkill().similarityScore());
        assertEquals(null, dictionaryStore.findByNormalizedName("springboot").orElse(null));
    }

    @Test
    void recommendationBulkApplyPromotesNewSkillCandidateThroughApproveFlow() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillRecommendationStore recommendationStore = new InMemorySkillRecommendationStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        DefaultSkillCandidateRecommendationService recommendationService = new DefaultSkillCandidateRecommendationService(
                recommendationStore, candidateStore, dictionaryStore, reviewService);
        var extraction = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "Kubernetes"));
        String candidateId = extraction.candidates().get(0).candidateId();
        recommendationStore.saveEmbedding("SKILL_CANDIDATE", candidateId, "kure", "model", 2,
                "Kubernetes", List.of(1.0d, 0.0d));

        var job = recommendationService.createJob(new SkillCandidateRecommendationJobCommand(
                "SELECTED", List.of(candidateId), null, null, null, null, "kure", "model", 2,
                List.of("SKILL_DICTIONARY"), 5, 0.75d, 0.6d, 0.92d));
        var applied = recommendationService.applyJob(job.jobId(), new SkillRecommendationApplyCommand(
                "ELIGIBLE_ONLY", List.of("NEW_SKILL_CANDIDATE", "EXISTING_SKILL_MATCH"), 0.6d, 0.92d));

        assertEquals(1, applied.appliedCount());
        assertEquals(SkillCandidateStatus.APPROVED, candidateStore.findCandidate(candidateId).orElseThrow().status());
        assertFalse(dictionaryStore.findByNormalizedName("kubernetes").isEmpty());
    }

    @Test
    void recommendationAnalysisRequiresCandidateEmbedding() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillRecommendationStore recommendationStore = new InMemorySkillRecommendationStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        DefaultSkillCandidateRecommendationService recommendationService = new DefaultSkillCandidateRecommendationService(
                recommendationStore, candidateStore, dictionaryStore, reviewService);
        var extraction = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "Kubernetes"));

        assertThrows(IllegalArgumentException.class,
                () -> recommendationService.createJob(new SkillCandidateRecommendationJobCommand(
                        "SELECTED", List.of(extraction.candidates().get(0).candidateId()), null, null, null, null,
                        "kure", "model", 2, List.of("SKILL_DICTIONARY"), 5, 0.75d, 0.6d, 0.92d)));
    }

    @Test
    void recommendationBulkApplyConnectsExistingSkillAndSkipsUnsupportedTypes() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        InMemorySkillRecommendationStore recommendationStore = new InMemorySkillRecommendationStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                new PatternSkillCandidateExtractor());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        DefaultSkillCandidateRecommendationService recommendationService = new DefaultSkillCandidateRecommendationService(
                recommendationStore, candidateStore, dictionaryStore, reviewService);
        var extraction = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "SpringBoot"));
        String candidateId = extraction.candidates().get(0).candidateId();
        recommendationStore.saveEmbedding("SKILL_CANDIDATE", candidateId, "kure", "model", 2,
                "SpringBoot", List.of(1.0d, 0.0d));
        recommendationStore.saveEmbedding("SKILL_DICTIONARY", "skill-1", "kure", "model", 2,
                "Spring Boot", List.of(1.0d, 0.0d));
        recommendationStore.saveEmbedding("DATASET_CONCEPT", "ncs-1", "kure", "model", 2,
                "Spring Framework", List.of(0.9d, 0.1d));

        var job = recommendationService.createJob(new SkillCandidateRecommendationJobCommand(
                "SELECTED", List.of(candidateId), null, null, null, null, "kure", "model", 2,
                List.of("SKILL_DICTIONARY", "DATASET_CONCEPT"), 5, 0.75d, 0.8d, 0.92d));
        var applied = recommendationService.applyJob(job.jobId(), new SkillRecommendationApplyCommand(
                "ELIGIBLE_ONLY", List.of("NEW_SKILL_CANDIDATE", "EXISTING_SKILL_MATCH"), 0.0d, 0.92d));

        assertEquals(1, applied.appliedCount());
        assertEquals(1, applied.skippedCount());
        var candidate = candidateStore.findCandidate(candidateId).orElseThrow();
        assertEquals(SkillCandidateStatus.ALIAS_CANDIDATE, candidate.status());
        assertEquals("skill-1", candidate.matchedSkillId());
    }

    @Test
    void duplicateNormalizedTermIncrementsOccurrenceWithinSameSourceChunk() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor());

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
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor());

        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));
        service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Kubernetes"));

        var candidates = store.searchCandidates(null, "kubernetes", 10);
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).occurrenceCount());
    }

    @Test
    void rejectsOversizedExtractionText() {
        InMemorySkillCandidateStore store = new InMemorySkillCandidateStore();
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(store, new PatternSkillCandidateExtractor());

        assertThrows(IllegalArgumentException.class,
                () -> service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "x".repeat(200_001))));
    }

    @Test
    void exactDictionaryMatchMarksCandidateAsMatched() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(candidateStore, dictionaryStore,
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
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(candidateStore, dictionaryStore,
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
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(candidateStore, dictionaryStore,
                new PatternSkillCandidateExtractor(), text -> List.of(0.9d, 0.4d),
                new SkillMatchPolicy(0.98d, 0.80d));

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "SpringBoot"));

        assertEquals(SkillCandidateStatus.ALIAS_CANDIDATE, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
        assertEquals(0.9138115486202572d, result.candidates().get(0).similarityScore());
        assertEquals("skill-1", result.candidates().get(0).matchedSkill().skillId());
        assertEquals("Spring Boot", result.candidates().get(0).matchedSkill().name());
        assertEquals(0.9138115486202572d, result.candidates().get(0).matchedSkill().similarityScore());
    }

    @Test
    void embeddingSimilarityUsesSearchTextWhenPresent() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "PostgreSQL vector search", "postgresql vector search",
                null, "ACTIVE", Instant.now(), Instant.now()), List.of(1.0d, 0.0d));
        List<String> embeddedTexts = new java.util.ArrayList<>();
        SkillCandidateExtractor extractor = text -> List.of(new SkillCandidateExtractor.ExtractedSkillTerm(
                "pgvector",
                "PostgreSQL pgvector 기반 NCS 벡터 유사도 검색 구현",
                "TECH_SKILL",
                "구현",
                List.of("PostgreSQL", "pgvector"),
                "NCS 벡터 검색",
                "pgvector 검색",
                "skillgraph",
                "ADVANCED",
                0.91d));
        DefaultSkillExtractionService service = new DefaultSkillExtractionService(candidateStore, dictionaryStore,
                extractor, text -> {
                    embeddedTexts.add(text);
                    return List.of(0.9d, 0.4d);
                }, new SkillMatchPolicy(0.98d, 0.80d));

        var result = service.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "pgvector 검색"));

        assertFalse(embeddedTexts.isEmpty());
        assertEquals("PostgreSQL pgvector 기반 NCS 벡터 유사도 검색 구현", embeddedTexts.get(0));
        assertEquals("PostgreSQL pgvector 기반 NCS 벡터 유사도 검색 구현", result.candidates().get(0).searchText());
        assertEquals("ADVANCED", result.candidates().get(0).difficulty());
        assertEquals(SkillCandidateStatus.ALIAS_CANDIDATE, result.candidates().get(0).status());
    }

    @Test
    void candidateEmbeddingJobEmbedsMissingCandidatesOnly() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        SkillCandidateExtractor extractor = text -> List.of(
                new SkillCandidateExtractor.ExtractedSkillTerm(
                        "Spring Boot REST API 구현",
                        "Spring Boot 기반 REST API 구현 역량",
                        "TECH_SKILL",
                        "구현",
                        List.of("Spring Boot"),
                        "REST API",
                        "Spring Boot API",
                        "backend",
                        "INTERMEDIATE",
                        0.91d));
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore, extractor);
        extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "Spring Boot"));
        List<String> embeddedTexts = new java.util.ArrayList<>();
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(
                candidateStore,
                null,
                new SkillEmbeddingPort() {
                    @Override
                    public List<Double> embedSkill(String text) {
                        return embedSkill(text, null, null);
                    }

                    @Override
                    public List<Double> embedSkill(String text, String provider, String model) {
                        embeddedTexts.add(provider + "/" + model + "/" + text);
                        return List.of(0.1d, 0.2d, 0.3d);
                    }
                });

        var first = reviewService.embedMissing("test-provider", "test-model", 3, 10);
        var second = reviewService.embedMissing("test-provider", "test-model", 3, 10);

        assertEquals(1, first.processedCount());
        assertEquals(0, first.failedCount());
        assertEquals(0, second.requestedCount());
        assertEquals(List.of("test-provider/test-model/Spring Boot 기반 REST API 구현 역량"), embeddedTexts);
    }

    @Test
    void reviewLookupReturnsMatchedDictionarySkill() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()), List.of(1.0d, 0.0d));
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                dictionaryStore, new PatternSkillCandidateExtractor(), text -> List.of(0.9d, 0.4d),
                new SkillMatchPolicy(0.98d, 0.80d));
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1",
                "SpringBoot"));

        var candidate = reviewService.get(result.candidates().get(0).candidateId());

        assertEquals("skill-1", candidate.matchedSkill().skillId());
        assertEquals("Spring Boot", candidate.matchedSkill().name());
        assertEquals(0.9138115486202572d, candidate.matchedSkill().similarityScore());
    }

    @Test
    void approvingNewCandidateAddsDictionaryEntry() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
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
        DefaultSkillExtractionService extractionService = new DefaultSkillExtractionService(candidateStore,
                dictionaryStore, new PatternSkillCandidateExtractor(), text -> List.of(), SkillMatchPolicy.defaults());
        DefaultSkillCandidateReviewService reviewService = new DefaultSkillCandidateReviewService(candidateStore,
                dictionaryStore);
        var result = extractionService.extract(new SkillExtractionCommand("course", "course-1", "chunk-1", "SpringBoot"));

        reviewService.review(result.candidates().get(0).candidateId(),
                new SkillCandidateReviewCommand(SkillCandidateStatus.ALIAS_CANDIDATE, "skill-1", "alias"));

        assertFalse(dictionaryStore.findByNormalizedAlias("springboot").isEmpty());
    }
}

package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJobStatus;
import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.application.service.DefaultSkillTaxonomyService;
import studio.one.platform.skillgraph.application.service.DuplicateSkillDictionaryException;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillTaxonomyStore;

class DefaultSkillDictionaryServiceTest {

    @Test
    void createsDictionarySkillAndSearchesIt() {
        InMemorySkillTaxonomyStore taxonomyStore = new InMemorySkillTaxonomyStore();
        new DefaultSkillTaxonomyService(taxonomyStore).saveCategory(new SkillCategoryCommand("backend", null,
                "Backend", 1));
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore(),
                taxonomyStore, null, null);

        var created = service.create(new CreateSkillDictionaryCommand(
                "Spring Boot",
                null,
                "backend",
                null,
                null));

        var searched = service.search("spring", PageRequest.of(0, 10));

        assertFalse(created.skillId().isBlank());
        assertEquals("Spring Boot", created.name());
        assertEquals("spring boot", created.normalizedName());
        assertEquals("backend", created.categoryId());
        assertEquals("Backend", created.categoryName());
        assertEquals("ACTIVE", created.status());

        assertEquals(1, searched.getTotalElements());
        assertEquals(1, searched.getContent().size());
        assertEquals(created.skillId(), searched.getContent().get(0).skillId());
        assertEquals("Backend", searched.getContent().get(0).categoryName());
    }

    @Test
    void rejectsDuplicateNormalizedName() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());
        service.create(new CreateSkillDictionaryCommand("Spring Boot", null, null, null, null));

        assertThrows(DuplicateSkillDictionaryException.class,
                () -> service.create(new CreateSkillDictionaryCommand("SpringBoot", "spring boot", null, null, null)));
    }

    @Test
    void usesExplicitNormalizedName() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());

        var created = service.create(new CreateSkillDictionaryCommand(
                "SpringBoot",
                "Spring Boot",
                null,
                "DRAFT",
                null));

        assertEquals("spring boot", created.normalizedName());
        assertEquals("DRAFT", created.status());
    }

    @Test
    void searchesWithPageAndSize() {
        SkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());

        service.create(new CreateSkillDictionaryCommand("A Skill", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("B Skill", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("C Skill", null, null, null, null));

        var firstPage = service.search(null, PageRequest.of(0, 2));
        var secondPage = service.search(null, PageRequest.of(1, 2));

        assertEquals(2, firstPage.getNumberOfElements());
        assertTrue(firstPage.hasNext());

        assertEquals(1, secondPage.getNumberOfElements());
        assertFalse(secondPage.hasNext());
    }

    @Test
    void embedsMissingDictionarySkillsOnly() {
        InMemorySkillDictionaryStore store = new InMemorySkillDictionaryStore();
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(
                store,
                text -> List.of(1.0d, 0.0d, 0.5d));
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Vue 컴포넌트 개발", null, null, null, null));

        var first = service.embedMissing(10);
        var second = service.embedMissing(10);

        assertEquals(2, first.totalMissingCount());
        assertEquals(2, first.requestedCount());
        assertEquals(2, first.processedCount());
        assertEquals(0, first.skippedCount());
        assertEquals(0, first.failedCount());
        assertEquals(SkillDictionaryEmbeddingJobStatus.COMPLETED, first.status());
        assertFalse(first.jobId().isBlank());
        assertEquals(2, store.findVectorItems(10).size());
        assertEquals(0, second.totalMissingCount());
        assertEquals(0, second.requestedCount());
        assertEquals(0, second.processedCount());
    }

    @Test
    void embeddingResultSeparatesTotalMissingFromBatchSize() {
        InMemorySkillDictionaryStore store = new InMemorySkillDictionaryStore();
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(
                store,
                text -> List.of(1.0d, 0.0d, 0.5d));
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Vue 컴포넌트 개발", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("PostgreSQL 벡터 검색 구현", null, null, null, null));

        var result = service.embedMissing(1);

        assertEquals(3, result.totalMissingCount());
        assertEquals(1, result.requestedCount());
        assertEquals(1, result.processedCount());
        assertEquals(2, result.skippedCount());
    }

    @Test
    void embeddingJobCanBeQueriedByJobId() {
        InMemorySkillDictionaryStore store = new InMemorySkillDictionaryStore();
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(
                store,
                text -> List.of(1.0d, 0.0d, 0.5d));
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));

        var result = service.embedMissing(10);
        var job = service.getEmbeddingJob(result.jobId());

        assertEquals(result.jobId(), job.jobId());
        assertEquals(SkillDictionaryEmbeddingJobStatus.COMPLETED, job.status());
        assertEquals(1, job.totalCount());
        assertEquals(1, job.requestedCount());
        assertEquals(1, job.processedCount());
        assertEquals(0, job.failedCount());
        assertEquals(0, job.skippedCount());
    }

    @Test
    void embeddingJobReportsProviderFailureMessage() {
        InMemorySkillDictionaryStore store = new InMemorySkillDictionaryStore();
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(
                store,
                text -> List.of());
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));

        var result = service.embedMissing(10);
        var job = service.getEmbeddingJob(result.jobId());

        assertEquals(SkillDictionaryEmbeddingJobStatus.FAILED, result.status());
        assertEquals(SkillDictionaryEmbeddingJobStatus.FAILED, job.status());
        assertEquals(0, job.processedCount());
        assertEquals(1, job.failedCount());
        assertEquals("Embedding job completed with failures: Embedding provider returned an empty vector",
                job.message());
    }
}

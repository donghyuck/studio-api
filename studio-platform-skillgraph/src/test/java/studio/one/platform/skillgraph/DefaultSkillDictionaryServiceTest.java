package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJobStatus;
import studio.one.platform.skillgraph.application.service.DefaultSkillDictionaryService;
import studio.one.platform.skillgraph.application.service.DuplicateSkillDictionaryException;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;

class DefaultSkillDictionaryServiceTest {

    @Test
    void createsDictionarySkillAndSearchesIt() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());

        var created = service.create(new CreateSkillDictionaryCommand(
                "Spring Boot",
                null,
                "backend",
                null,
                null));
        var searched = service.search("spring", 10);

        assertFalse(created.skillId().isBlank());
        assertEquals("Spring Boot", created.name());
        assertEquals("spring boot", created.normalizedName());
        assertEquals("backend", created.categoryId());
        assertEquals("ACTIVE", created.status());
        assertEquals(1, searched.size());
        assertEquals(created.skillId(), searched.get(0).skillId());
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
    void searchesWithOffsetAndLimit() {
        DefaultSkillDictionaryService service = new DefaultSkillDictionaryService(new InMemorySkillDictionaryStore());
        service.create(new CreateSkillDictionaryCommand("Backend REST API 설계", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Frontend Vue 컴포넌트 개발", null, null, null, null));
        service.create(new CreateSkillDictionaryCommand("Spring Security JWT 인증 구성", null, null, null, null));

        var page = service.search(null, 1, 2);

        assertEquals(2, page.size());
        assertEquals("Frontend Vue 컴포넌트 개발", page.get(0).name());
        assertEquals("Spring Security JWT 인증 구성", page.get(1).name());
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
        assertEquals("Embedding job completed with failures: Embedding provider returned an empty vector", job.message());
    }
}

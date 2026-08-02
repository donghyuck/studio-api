package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;

class WebKnowledgeCrawlStatePersistenceTest {

    @Test
    void executesCompletionActionsThroughIndependentTransaction() {
        TransactionOperations transactions = transactions();
        WebKnowledgeCrawlStatePersistence persistence = new WebKnowledgeCrawlStatePersistence(
                mock(WebKnowledgeSourceJpaRepository.class),
                mock(WebKnowledgeCrawlRunJpaRepository.class),
                transactions);
        AtomicBoolean executed = new AtomicBoolean();

        persistence.execute(() -> executed.set(true));

        assertThat(executed).isTrue();
        verify(transactions).execute(any());
    }

    @Test
    void commitsStartAndTerminalProgressThroughIndependentTransaction() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeCrawlRunJpaRepository runs = mock(WebKnowledgeCrawlRunJpaRepository.class);
        TransactionOperations transactions = transactions();
        Instant created = Instant.parse("2026-07-31T00:00:00Z");
        Instant started = created.plusSeconds(1);
        WebKnowledgeSourceEntity managedSource = source("wsrc-1", created);
        WebKnowledgeCrawlRunEntity managedRun = run("wrun-1", created);
        when(sources.findById("wsrc-1")).thenReturn(java.util.Optional.of(managedSource));
        when(runs.findById("wrun-1")).thenReturn(java.util.Optional.of(managedRun));
        WebKnowledgeCrawlStatePersistence persistence =
                new WebKnowledgeCrawlStatePersistence(sources, runs, transactions);

        persistence.start("wrun-1", "wsrc-1", "worker-1", started.plusSeconds(120), started);

        assertThat(managedRun.status()).isEqualTo("DISCOVERING");
        assertThat(managedRun.heartbeatAt()).isEqualTo(started);
        assertThat(managedSource.status()).isEqualTo("FETCHING");

        WebKnowledgeCrawlRunEntity snapshotRun = run("wrun-1", created);
        snapshotRun.start("worker-1", started.plusSeconds(120), started);
        snapshotRun.fail("WEB_CRAWL_EMBEDDING_TIMEOUT", started.plusSeconds(5));
        WebKnowledgeSourceEntity snapshotSource = source("wsrc-1", created);
        snapshotSource.status("FAILED", started.plusSeconds(5));
        WebKnowledgeCrawlProgressSnapshot progress =
                new WebKnowledgeCrawlProgressSnapshot(3, 1, 0, 0, 0, 0, 1, 0, 2048, 512);

        persistence.save(
                snapshotRun,
                snapshotSource,
                progress,
                started.plusSeconds(5),
                started.plusSeconds(125));

        assertThat(managedRun.status()).isEqualTo("FAILED");
        assertThat(managedRun.errorCode()).isEqualTo("WEB_CRAWL_EMBEDDING_TIMEOUT");
        assertThat(managedRun.discoveredCount()).isEqualTo(3);
        assertThat(managedRun.fetchedCount()).isEqualTo(1);
        assertThat(managedRun.responseBytes()).isEqualTo(2048);
        assertThat(managedSource.status()).isEqualTo("FAILED");
        verify(runs, times(2)).saveAndFlush(managedRun);
        verify(sources, times(2)).saveAndFlush(managedSource);
    }

    private static TransactionOperations transactions() {
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                        .doInTransaction(mock(TransactionStatus.class)));
        return transactions;
    }

    private static WebKnowledgeCrawlRunEntity run(String runId, Instant now) {
        return new WebKnowledgeCrawlRunEntity(
                runId, 1L, "wsrc-1", null, "{}", "policy-hash", "tester", now);
    }

    private static WebKnowledgeSourceEntity source(String sourceId, Instant now) {
        return new WebKnowledgeSourceEntity(
                sourceId,
                1L,
                "https://example.org",
                "https://example.org/",
                "url-hash",
                "example.org",
                "Example",
                "embedding-default",
                "space-1",
                "SITE",
                "{}",
                "policy-hash",
                "tester",
                now);
    }
}

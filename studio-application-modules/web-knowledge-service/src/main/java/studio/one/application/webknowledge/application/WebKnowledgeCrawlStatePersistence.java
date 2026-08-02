package studio.one.application.webknowledge.application;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeCrawlRunJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;

/**
 * Persists crawl state in short, independent transactions so a long-running
 * fetch or embedding request cannot hide progress from status readers.
 */
public class WebKnowledgeCrawlStatePersistence {

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeCrawlRunJpaRepository runs;
    private final TransactionOperations transactions;

    public WebKnowledgeCrawlStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeCrawlRunJpaRepository runs,
            EntityManagerFactory entityManagerFactory) {
        this(sources, runs, requiresNew(entityManagerFactory));
    }

    WebKnowledgeCrawlStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeCrawlRunJpaRepository runs,
            TransactionOperations transactions) {
        this.sources = sources;
        this.runs = runs;
        this.transactions = transactions;
    }

    public void start(
            String runId,
            String sourceId,
            String leaseOwner,
            Instant leaseUntil,
            Instant now) {
        Objects.requireNonNull(transactions.execute(status -> {
            WebKnowledgeCrawlRunEntity run = requiredRun(runId);
            WebKnowledgeSourceEntity source = requiredSource(sourceId);
            run.start(leaseOwner, leaseUntil, now);
            source.status("FETCHING", now);
            runs.saveAndFlush(run);
            sources.saveAndFlush(source);
            return Boolean.TRUE;
        }));
    }

    public void save(
            WebKnowledgeCrawlRunEntity snapshot,
            WebKnowledgeSourceEntity sourceSnapshot,
            WebKnowledgeCrawlProgressSnapshot progress,
            Instant now,
            Instant leaseUntil) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(transactions.execute(status -> {
            WebKnowledgeCrawlRunEntity run = requiredRun(snapshot.runId());
            WebKnowledgeSourceEntity source = requiredSource(sourceSnapshot.sourceId());
            run.progress(
                    progress.discovered(),
                    progress.fetched(),
                    progress.indexed(),
                    progress.unchanged(),
                    progress.updated(),
                    progress.removed(),
                    progress.failed(),
                    progress.skipped(),
                    progress.responseBytes(),
                    progress.normalizedChars(),
                    now);
            if (snapshot.truncated() && !run.truncated()) {
                run.truncate(snapshot.truncationReason(), now);
            }
            applyRunState(run, snapshot, now, leaseUntil);
            applySourceState(source, sourceSnapshot, now);
            runs.saveAndFlush(run);
            sources.saveAndFlush(source);
            return Boolean.TRUE;
        }));
    }

    public <T> T execute(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return transactions.execute(status -> action.get());
    }

    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(transactions.execute(status -> {
            action.run();
            return Boolean.TRUE;
        }));
    }

    private void applyRunState(
            WebKnowledgeCrawlRunEntity run,
            WebKnowledgeCrawlRunEntity snapshot,
            Instant now,
            Instant leaseUntil) {
        if ("FAILED".equals(snapshot.status())) {
            run.fail(snapshot.errorCode(), now);
        } else if (isTerminal(snapshot.status())) {
            run.complete(snapshot.status(), now);
        } else {
            run.status(snapshot.status(), now);
            run.heartbeat(snapshot.leaseOwner(), leaseUntil, now);
        }
    }

    private void applySourceState(
            WebKnowledgeSourceEntity source,
            WebKnowledgeSourceEntity snapshot,
            Instant now) {
        if ("COMPLETED".equals(snapshot.status()) && snapshot.currentCorpusRevisionId() != null) {
            source.completeCorpus(
                    snapshot.currentCorpusRevisionId(),
                    snapshot.canonicalUrl(),
                    snapshot.embeddingSpaceId(),
                    snapshot.displayName(),
                    now);
        } else if ("UNCHANGED".equals(snapshot.status())) {
            source.unchanged(now);
        } else {
            source.status(snapshot.status(), now);
        }
    }

    private WebKnowledgeCrawlRunEntity requiredRun(String runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("WEB_CRAWL_RUN_NOT_FOUND"));
    }

    private WebKnowledgeSourceEntity requiredSource(String sourceId) {
        return sources.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("WEB_SOURCE_NOT_FOUND"));
    }

    private static boolean isTerminal(String status) {
        return "COMPLETED".equals(status)
                || "PARTIAL".equals(status)
                || "UNCHANGED".equals(status)
                || "CANCELLED".equals(status);
    }

    private static TransactionOperations requiresNew(EntityManagerFactory entityManagerFactory) {
        TransactionTemplate template = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}

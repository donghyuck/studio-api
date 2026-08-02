package studio.one.application.webknowledge.application;

import java.util.Objects;

import jakarta.persistence.EntityManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;

/**
 * Persists web knowledge execution state independently from the long-running
 * fetch and indexing operation.
 */
public class WebKnowledgeStatePersistence {

    private static final Logger log = LoggerFactory.getLogger(WebKnowledgeStatePersistence.class);

    private final WebKnowledgeSourceJpaRepository sources;
    private final WebKnowledgeRevisionJpaRepository revisions;
    private final TransactionOperations transactions;

    public WebKnowledgeStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            EntityManagerFactory entityManagerFactory) {
        this(sources, revisions, requiresNew(entityManagerFactory));
    }

    WebKnowledgeStatePersistence(
            WebKnowledgeSourceJpaRepository sources,
            WebKnowledgeRevisionJpaRepository revisions,
            TransactionOperations transactions) {
        this.sources = sources;
        this.revisions = revisions;
        this.transactions = transactions;
    }

    public PersistedState save(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision) {
        log.debug(
                "Persisting web knowledge state: sourceStatus={}, revisionStatus={}",
                source.status(),
                revision.status());
        return Objects.requireNonNull(transactions.execute(status -> {
            WebKnowledgeRevisionEntity persistedRevision = revisions.saveAndFlush(revision);
            WebKnowledgeSourceEntity persistedSource = sources.saveAndFlush(source);
            log.debug(
                    "Persisted web knowledge state: sourceStatus={}, revisionStatus={}",
                    persistedSource.status(),
                    persistedRevision.status());
            return new PersistedState(persistedSource, persistedRevision);
        }));
    }

    public record PersistedState(
            WebKnowledgeSourceEntity source,
            WebKnowledgeRevisionEntity revision) {
    }

    private static TransactionOperations requiresNew(EntityManagerFactory entityManagerFactory) {
        TransactionTemplate template = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}

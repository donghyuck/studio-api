package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeRevisionJpaRepository;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;

class WebKnowledgeStatePersistenceTest {

    @Test
    void savesRevisionAndSourceAsOnePersistedState() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        WebKnowledgeRevisionJpaRepository revisions = mock(WebKnowledgeRevisionJpaRepository.class);
        WebKnowledgeSourceEntity source = source("wsrc-1");
        WebKnowledgeRevisionEntity revision = new WebKnowledgeRevisionEntity(
                "wrev-1", source.sourceId(), Instant.now());
        WebKnowledgeSourceEntity persistedSource = source("wsrc-1");
        WebKnowledgeRevisionEntity persistedRevision = new WebKnowledgeRevisionEntity(
                "wrev-1", source.sourceId(), Instant.now());
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(revisions.saveAndFlush(revision)).thenReturn(persistedRevision);
        when(sources.saveAndFlush(source)).thenReturn(persistedSource);
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<org.springframework.transaction.support.TransactionCallback<?>>getArgument(0)
                        .doInTransaction(mock(TransactionStatus.class)));

        WebKnowledgeStatePersistence.PersistedState result =
                new WebKnowledgeStatePersistence(sources, revisions, transactions).save(source, revision);

        assertSame(persistedSource, result.source());
        assertSame(persistedRevision, result.revision());
    }

    private static WebKnowledgeSourceEntity source(String sourceId) {
        return new WebKnowledgeSourceEntity(
                sourceId,
                1L,
                "https://example.org/article",
                "https://example.org/article",
                "hash",
                "example.org",
                "reference",
                "embedding-default",
                "space-1",
                "tester",
                Instant.now());
    }
}

package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class WebKnowledgeSiteCrawlCoordinatorProxyContractTest {

    @Test
    void transactionalCoordinatorCanBeSubclassProxied() throws Exception {
        assertFalse(Modifier.isFinal(WebKnowledgeSiteCrawlCoordinator.class.getModifiers()));
        assertNotNull(WebKnowledgeSiteCrawlCoordinator.class
                .getMethod(
                        "createRun",
                        studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity.class,
                        ResolvedWebCrawlPolicy.class,
                        String.class,
                        String.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void longRunningExecutionDoesNotJoinAnAmbientTransaction() throws Exception {
        Transactional transactional = WebKnowledgeSiteCrawlCoordinator.class
                .getMethod("execute", String.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        org.junit.jupiter.api.Assertions.assertEquals(
                Propagation.NOT_SUPPORTED,
                transactional.propagation());
    }
}

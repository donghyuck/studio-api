package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.service.pipeline.EmbeddingProviderTimeoutException;

class WebKnowledgeSiteCrawlCoordinatorErrorCodeTest {

    @Test
    void mapsEmbeddingTimeoutToStableCrawlErrorCode() {
        RuntimeException failure = new RuntimeException(
                "indexing failed",
                new EmbeddingProviderTimeoutException(
                        "Embedding provider timed out.",
                        new SocketTimeoutException("timed out")));

        assertThat(WebKnowledgeSiteCrawlCoordinator.errorCode(failure))
                .isEqualTo("WEB_CRAWL_EMBEDDING_TIMEOUT");
    }
}

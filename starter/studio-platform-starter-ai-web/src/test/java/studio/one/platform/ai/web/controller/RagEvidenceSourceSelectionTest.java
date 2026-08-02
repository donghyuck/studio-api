package studio.one.platform.ai.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;
import studio.one.platform.ai.web.dto.IndexedWebSourceRefDto;

class RagEvidenceSourceSelectionTest {

    @Test
    void resolvesPinnedSourcesAndProducesStableFingerprint() {
        RagEvidenceSourceSelection first = resolve(List.of(
                new IndexedWebSourceRefDto("b", "r2"),
                new IndexedWebSourceRefDto("a", "r1")));
        RagEvidenceSourceSelection second = resolve(List.of(
                new IndexedWebSourceRefDto("a", "r1"),
                new IndexedWebSourceRefDto("b", "r2")));

        assertEquals(2, first.indexedSources().size());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals("embedding-default", first.embeddingDeploymentId());
        assertEquals("space-1", first.embeddingSpaceId());
    }

    @Test
    void hidesUnauthorizedSourceAsNotFound() {
        RagObjectAuthorizationRouter denied = new RagObjectAuthorizationRouter(
                org.mockito.Mockito.mock(ApplicationContext.class),
                List.of(authorizer(false)));

        assertThrows(ResponseStatusException.class, () -> RagEvidenceSourceSelection.resolve(
                List.of(new IndexedWebSourceRefDto("a", "r1")),
                List.of(provider()),
                denied,
                10));
    }

    private RagEvidenceSourceSelection resolve(List<IndexedWebSourceRefDto> refs) {
        RagObjectAuthorizationRouter allowed = new RagObjectAuthorizationRouter(
                org.mockito.Mockito.mock(ApplicationContext.class),
                List.of(authorizer(true)));
        return RagEvidenceSourceSelection.resolve(refs, List.of(provider()), allowed, 10);
    }

    private RagObjectAuthorizer authorizer(boolean allowed) {
        return new RagObjectAuthorizer() {
            @Override
            public boolean supports(String objectType) {
                return "web_source".equals(objectType);
            }

            @Override
            public boolean canRead(String objectType, String objectId) {
                return allowed;
            }
        };
    }

    private IndexedRagSourceProvider provider() {
        return new IndexedRagSourceProvider() {
            @Override
            public boolean supports(String sourceType) {
                return "web_source".equals(sourceType);
            }

            @Override
            public Optional<ResolvedIndexedRagSource> resolve(String sourceId, String revisionId) {
                return Optional.of(new ResolvedIndexedRagSource(
                        "web_source",
                        sourceId,
                        revisionId,
                        "web_source",
                        sourceId,
                        "hash-" + revisionId,
                        "embedding-default",
                        "space-1",
                        Map.of()));
            }
        };
    }
}

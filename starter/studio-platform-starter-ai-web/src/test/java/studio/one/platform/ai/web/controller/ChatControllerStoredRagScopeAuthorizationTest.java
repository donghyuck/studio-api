package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatControllerStoredRagScopeAuthorizationTest {

    @Test
    void requiresCurrentAccessToBaseObjectAndEveryIndexedWebSource() {
        RagObjectAuthorizationRouter router = mock(RagObjectAuthorizationRouter.class);
        when(router.canReadRagService()).thenReturn(true);
        when(router.canRead("attachment", "11")).thenReturn(true);
        when(router.canRead("web_source", "source-a")).thenReturn(true);
        when(router.canRead("web_source", "source-b")).thenReturn(false);

        Map<String, Object> metadata = Map.of(
                "ragObjectType", "attachment",
                "ragObjectId", "11",
                "indexedWebSources", indexedSources("source-a", "source-b"));

        assertThat(ChatController.canReadStoredRagScope(metadata, router)).isFalse();

        when(router.canRead("web_source", "source-b")).thenReturn(true);
        assertThat(ChatController.canReadStoredRagScope(metadata, router)).isTrue();
    }

    @Test
    void supportsWebOnlyAndUnscopedRagTurnsWithServicePermission() {
        RagObjectAuthorizationRouter router = mock(RagObjectAuthorizationRouter.class);
        when(router.canReadRagService()).thenReturn(true);
        when(router.canRead("web_source", "source-a")).thenReturn(true);

        assertThat(ChatController.canReadStoredRagScope(
                Map.of("indexedWebSources", indexedSources("source-a")),
                router)).isTrue();
        assertThat(ChatController.canReadStoredRagScope(Map.of(), router)).isTrue();
    }

    @Test
    void failsClosedForMalformedOrIncompleteStoredScopes() {
        RagObjectAuthorizationRouter router = mock(RagObjectAuthorizationRouter.class);
        when(router.canReadRagService()).thenReturn(true);

        assertThat(ChatController.canReadStoredRagScope(
                Map.of("ragObjectType", "attachment"),
                router)).isFalse();
        assertThat(ChatController.canReadStoredRagScope(
                Map.of("indexedWebSources", Map.of("count", 1, "sources", List.of())),
                router)).isFalse();
        assertThat(ChatController.canReadStoredRagScope(
                Map.of("indexedWebSources", "invalid"),
                router)).isFalse();
    }

    @Test
    void requiresRagServiceReadPermission() {
        RagObjectAuthorizationRouter router = mock(RagObjectAuthorizationRouter.class);
        when(router.canReadRagService()).thenReturn(false);

        assertThat(ChatController.canReadStoredRagScope(Map.of(), router)).isFalse();
    }

    private Map<String, Object> indexedSources(String... sourceIds) {
        List<Map<String, Object>> sources = java.util.Arrays.stream(sourceIds)
                .map(sourceId -> Map.<String, Object>of(
                        "sourceId", sourceId,
                        "revisionId", "revision-" + sourceId,
                        "partitionCount", 1))
                .toList();
        return Map.of("count", sources.size(), "sources", sources);
    }
}

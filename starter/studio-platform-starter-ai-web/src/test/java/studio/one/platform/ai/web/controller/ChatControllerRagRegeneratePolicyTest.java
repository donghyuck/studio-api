package studio.one.platform.ai.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChatControllerRagRegeneratePolicyTest {

    @Test
    void restoresStoredRequestedSourceScopeWhenRegenerating() {
        Map<String, Object> metadata = Map.of(
                "sourcePolicy", Map.of(
                        "requestedScope", "DOCUMENT_AND_OFFICIAL_EXTERNAL",
                        "effectiveScope", "DOCUMENT_AND_OFFICIAL_EXTERNAL"));

        assertEquals(
                "DOCUMENT_AND_OFFICIAL_EXTERNAL",
                ChatController.replaySourceScope(metadata, null));
    }

    @Test
    void rejectsChangingSourceScopeDuringRegeneration() {
        Map<String, Object> metadata = Map.of(
                "sourcePolicy", Map.of("effectiveScope", "DOCUMENT_ONLY"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ChatController.replaySourceScope(
                        metadata,
                        "DOCUMENT_AND_OFFICIAL_EXTERNAL"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("RAG_REGENERATION_SOURCE_SCOPE_MISMATCH", exception.getReason());
    }

    @Test
    void keepsRequestScopeForLegacyConversationWithoutSourcePolicy() {
        assertEquals(
                "DOCUMENT_ONLY",
                ChatController.replaySourceScope(Map.of(), "DOCUMENT_ONLY"));
    }
}

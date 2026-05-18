package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.service.DefaultSkillExtractionService;
import studio.one.platform.skillgraph.application.service.SkillMatchPolicy;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.infrastructure.extraction.LlmSkillCandidateExtractor;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillCandidateStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillDictionaryStore;

class LlmSkillCandidateExtractorTest {

    @Test
    void rendersPromptAndParsesJsonCandidates() {
        CapturingPromptRenderer promptRenderer = new CapturingPromptRenderer();
        CapturingChatPort chatPort = new CapturingChatPort("""
                [
                  {"term":"Spring Boot","confidence":0.93},
                  {"term":"JPA","confidence":1.2},
                  {"term":"spring boot","confidence":0.4},
                  {"term":"Kubernetes","confidence":"0.7"}
                ]
                """);
        SkillExtractionService service = service(promptRenderer, chatPort, 2);

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", null,
                "Spring Boot, JPA, Kubernetes"));

        assertEquals(2, result.extractedCount());
        assertEquals("Spring Boot", result.candidates().get(0).term());
        assertEquals(0.93d, result.candidates().get(0).confidence());
        assertEquals("JPA", result.candidates().get(1).term());
        assertEquals(1.0d, result.candidates().get(1).confidence());
        assertEquals("skill-extraction", promptRenderer.name);
        assertEquals(2, promptRenderer.params.get("maxTerms"));
        assertEquals("Spring", String.valueOf(promptRenderer.params.get("text")).substring(0, 6));
        assertEquals(128, chatPort.request.maxOutputTokens());
        assertEquals(0.0d, chatPort.request.temperature());
    }

    @Test
    void returnsEmptyCandidatesWhenLlmResponseIsMalformed() {
        SkillExtractionService service = service(new CapturingPromptRenderer(),
                new CapturingChatPort("not-json"), 10);

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", null, "Spring Boot"));

        assertEquals(0, result.extractedCount());
    }

    @Test
    void commonFlowAppliesDictionaryMatchAndDryRunDoesNotPersist() {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        InMemorySkillDictionaryStore dictionaryStore = new InMemorySkillDictionaryStore();
        dictionaryStore.save(new SkillDictionary("skill-1", "Spring Boot", "spring boot", null, "ACTIVE",
                Instant.now(), Instant.now()));
        LlmSkillCandidateExtractor extractor = extractor(
                candidateStore,
                dictionaryStore,
                new CapturingPromptRenderer(),
                new CapturingChatPort("[{\"term\":\"Spring Boot\",\"confidence\":0.9}]"),
                10);
        SkillExtractionService service = new DefaultSkillExtractionService(
                candidateStore,
                dictionaryStore,
                extractor,
                text -> List.of(),
                SkillMatchPolicy.defaults());

        var result = service.dryRun(new SkillExtractionCommand("simulation", "sample", null, "Spring Boot"));

        assertEquals(SkillCandidateStatus.MATCHED, result.candidates().get(0).status());
        assertEquals("skill-1", result.candidates().get(0).matchedSkillId());
        assertEquals(0, candidateStore.sourceChunks().size());
        assertEquals(0, candidateStore.searchCandidates(null, null, 100).size());
    }

    private SkillExtractionService service(
            PromptRenderer promptRenderer,
            ChatPort chatPort,
            int maxTerms) {
        InMemorySkillCandidateStore candidateStore = new InMemorySkillCandidateStore();
        return new DefaultSkillExtractionService(
                candidateStore,
                null,
                extractor(candidateStore, null, promptRenderer, chatPort, maxTerms),
                text -> List.of(),
                SkillMatchPolicy.defaults());
    }

    private LlmSkillCandidateExtractor extractor(
            SkillCandidateStore candidateStore,
            SkillDictionaryStore dictionaryStore,
            PromptRenderer promptRenderer,
            ChatPort chatPort,
            int maxTerms) {
        return new LlmSkillCandidateExtractor(
                candidateStore,
                dictionaryStore,
                text -> List.of(),
                SkillMatchPolicy.defaults(),
                promptRenderer,
                chatPort,
                new ObjectMapper(),
                "skill-extraction",
                maxTerms,
                6,
                128,
                0.0d);
    }

    private static final class CapturingPromptRenderer implements PromptRenderer {

        private String name;
        private Map<String, Object> params;

        @Override
        public String render(String name, Map<String, Object> params) {
            this.name = name;
            this.params = params;
            return "prompt: " + params.get("text");
        }

        @Override
        public String getRawPrompt(String name) {
            return render(name, Map.of());
        }
    }

    private static final class CapturingChatPort implements ChatPort {

        private final String response;
        private ChatRequest request;

        private CapturingChatPort(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = request;
            return new ChatResponse(List.of(ChatMessage.assistant(response)), "test-model", Map.of());
        }
    }
}

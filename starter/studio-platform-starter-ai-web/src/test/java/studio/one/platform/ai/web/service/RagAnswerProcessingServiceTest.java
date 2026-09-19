package studio.one.platform.ai.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.web.controller.PackedEvidenceSet;
import studio.one.platform.ai.web.controller.RagAnswerFinalizer;
import studio.one.platform.ai.web.controller.RagAnswerPolicyResolver;

class RagAnswerProcessingServiceTest {
    private final RagAnswerFinalizer finalizer = mock(RagAnswerFinalizer.class);
    private final RagAnswerProcessingService service = new RagAnswerProcessingService(finalizer);
    private final PackedEvidenceSet evidence = new PackedEvidenceSet("", List.of(), Map.of(), "test");

    @Test
    void processesLastNonBlankAssistantResponseWithoutHttpContext() {
        var policy = RagAnswerPolicyResolver.defaults().resolve(null);
        var response = new ChatResponse(List.of(ChatMessage.assistant("earlier"),
                ChatMessage.assistant("latest")), "model", Map.of());
        var expected = new RagAnswerFinalizer().finalizeAnswer("latest", evidence, policy, null);
        when(finalizer.finalizeAnswer("latest", evidence, policy, null)).thenReturn(expected);
        assertThat(service.process(response, evidence, policy, null, false)).isSameAs(expected);
        verify(finalizer).finalizeAnswer("latest", evidence, policy, null);
    }

    @Test
    void coverageFallbackAddsLimitationOnce() {
        String result = service.ensureInterpretiveFallbackLimitation("분석입니다.", evidence, true);
        assertThat(result).contains("확인 한계:").endsWith("[1]");
        assertThat(service.ensureInterpretiveFallbackLimitation(result, evidence, true)).isEqualTo(result);
    }

    @Test
    void leavesOrdinaryAndEmptyAnswersUnchanged() {
        assertThat(service.ensureInterpretiveFallbackLimitation("분석입니다.", evidence, false)).isEqualTo("분석입니다.");
        assertThat(service.ensureInterpretiveFallbackLimitation("", evidence, true)).isEmpty();
    }
}

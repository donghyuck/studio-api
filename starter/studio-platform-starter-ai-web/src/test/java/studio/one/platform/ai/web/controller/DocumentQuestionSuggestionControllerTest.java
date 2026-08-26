package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Availability;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Status;

class DocumentQuestionSuggestionControllerTest {

    @Test
    void returnsServiceResponseAndUsesObjectScopedReadAuthorization() throws Exception {
        DocumentQuestionSuggestionService service = mock(DocumentQuestionSuggestionService.class);
        DocumentQuestionSuggestionsResponseDto expected = new DocumentQuestionSuggestionsResponseDto(
                DocumentQuestionSuggestionPolicy.CONTRACT_VERSION,
                Instant.EPOCH,
                new DocumentQuestionSuggestionsResponseDto.Basis(
                        "attachment", "19", "document-19", "revision-1", "hash-1", "chunks-1"),
                new Availability(Status.NO_SIGNALS, List.of("QUESTION_SIGNALS_NOT_FOUND")),
                List.of(),
                new DocumentQuestionSuggestionPolicy().snapshot());
        when(service.suggest("attachment", "19")).thenReturn(expected);
        DocumentQuestionSuggestionController controller = new DocumentQuestionSuggestionController(service);

        var response = controller.suggestions("attachment", "19");

        assertThat(response.getBody().getData()).isEqualTo(expected);
        Method method = DocumentQuestionSuggestionController.class.getMethod(
                "suggestions", String.class, String.class);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/objects/{objectType}/{objectId}/question-suggestions");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("services:ai_rag", "@ragObjectAuthorizationRouter.canRead(#objectType, #objectId)")
                .doesNotContain("ragIndexJobEndpointSecurity")
                .doesNotContain("services:ai_chat");
    }

    @Test
    void usabilityUsesTheSameObjectScopedReadAuthorization() throws Exception {
        Method method = DocumentUsabilityController.class.getMethod(
                "usability", String.class, String.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("services:ai_rag", "@ragObjectAuthorizationRouter.canRead(#objectType, #objectId)")
                .doesNotContain("ragIndexJobEndpointSecurity")
                .doesNotContain("services:ai_chat");
    }
}

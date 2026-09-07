package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;

class InterpretiveRetrievalQueryPlannerTest {

    private final InterpretiveRetrievalQueryPlanner planner = new InterpretiveRetrievalQueryPlanner();

    @Test
    void combinesCurrentQuestionDocumentTitleAndRecentUserContextIntoBoundedQueries() {
        ChatRequestDto chat = new ChatRequestDto(
                null,
                null,
                List.of(
                        new ChatMessageDto("user", "Holden Caulfield와 Pencey Prep의 관계를 설명해줘"),
                        new ChatMessageDto("assistant", "이전 답변은 검색 맥락에 포함하지 않습니다."),
                        new ChatMessageDto("user", "이 소설에 청소년 권장 도서인 이유는")),
                null,
                null,
                null,
                null,
                null,
                null);

        InterpretiveRetrievalQueryPlanner.Plan plan = planner.plan(
                "이 소설에 청소년 권장 도서인 이유는",
                chat,
                "The Catcher in the Rye");

        assertThat(plan.queries()).hasSize(4).allSatisfy(query -> assertThat(query).hasSizeLessThanOrEqualTo(1_000));
        assertThat(plan.queries().get(0))
                .contains("The Catcher in the Rye")
                .contains("Holden Caulfield")
                .contains("Pencey Prep");
        assertThat(plan.queries().get(1))
                .contains("성장")
                .contains("character actions");
        assertThat(plan.documentTitleUsed()).isTrue();
        assertThat(plan.conversationContextUsed()).isTrue();
    }
}

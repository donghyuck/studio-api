package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;

/** Builds bounded, deterministic evidence queries for document-grounded interpretation. */
final class InterpretiveRetrievalQueryPlanner {

    private static final int MAX_QUERIES = 4;
    private static final int MAX_QUERY_CHARS = 1_000;
    private static final int MAX_CONTEXT_MESSAGES = 2;
    private static final int MAX_CONTEXT_CHARS = 500;
    private static final String KOREAN_EVIDENCE_DIMENSIONS =
            "행동 대화 사건 감정 갈등 변화 성장 정체성 소외 주제 의미 교훈 문서 근거";
    private static final String ENGLISH_EVIDENCE_DIMENSIONS =
            "character actions dialogue events emotions conflict change growth identity alienation theme meaning evidence";

    Plan plan(String question, ChatRequestDto chat, String documentTitle) {
        String normalizedQuestion = bounded(question, MAX_QUERY_CHARS);
        if (normalizedQuestion == null) {
            return new Plan(List.of(), false, false);
        }
        String title = bounded(documentTitle, 300);
        List<String> previousQuestions = previousUserQuestions(chat, normalizedQuestion);
        String conversationContext = previousQuestions.isEmpty()
                ? null
                : bounded(String.join(" / ", previousQuestions), MAX_CONTEXT_CHARS);

        Set<String> queries = new LinkedHashSet<>();
        add(queries, join(normalizedQuestion, title, conversationContext));
        add(queries, join(title, conversationContext, KOREAN_EVIDENCE_DIMENSIONS,
                ENGLISH_EVIDENCE_DIMENSIONS));
        add(queries, join(normalizedQuestion, title));
        add(queries, join(normalizedQuestion, conversationContext));

        return new Plan(
                queries.stream().limit(MAX_QUERIES).toList(),
                title != null,
                conversationContext != null);
    }

    private List<String> previousUserQuestions(ChatRequestDto chat, String currentQuestion) {
        if (chat == null || chat.messages() == null || chat.messages().isEmpty()) {
            return List.of();
        }
        List<String> previous = new ArrayList<>();
        for (int index = chat.messages().size() - 1; index >= 0 && previous.size() < MAX_CONTEXT_MESSAGES; index--) {
            ChatMessageDto message = chat.messages().get(index);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            String content = bounded(message.content(), MAX_CONTEXT_CHARS);
            if (content != null && !content.equalsIgnoreCase(currentQuestion)
                    && previous.stream().noneMatch(content::equalsIgnoreCase)) {
                previous.add(content);
            }
        }
        java.util.Collections.reverse(previous);
        return List.copyOf(previous);
    }

    private void add(Set<String> queries, String value) {
        String normalized = bounded(value, MAX_QUERY_CHARS);
        if (normalized != null) {
            queries.add(normalized);
        }
    }

    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(" ", parts);
    }

    private String bounded(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    record Plan(List<String> queries, boolean documentTitleUsed, boolean conversationContextUsed) {
        Plan {
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }
}

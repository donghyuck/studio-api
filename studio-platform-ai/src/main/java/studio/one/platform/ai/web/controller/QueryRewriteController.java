package studio.one.platform.ai.web.controller;

import javax.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptManager;
import studio.one.platform.ai.web.dto.QueryRewriteRequestDto;
import studio.one.platform.ai.web.dto.QueryRewriteResponseDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * 검색 품질 향상을 위해 쿼리 리라이트 프롬프트를 제공하는 API.
 * 모델 호출 전에 정규화된 검색 쿼리를 얻거나, 프롬프트를 직접 활용할 수 있다.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/query-rewrite")
@Validated
@RequiredArgsConstructor
@Slf4j
public class QueryRewriteController {

    private static final String TEMPLATE_NAME = "query-rewrite";
    private static final String FALLBACK_PROMPT = """
            You are a "Query Optimizer" for a Semantic Search system.

            Users usually type natural, informal search queries, and directly embedding these queries
            often reduces search accuracy. Your task is to rewrite the user's query into an
            "Intention-Expanded Query" that improves retrieval quality for vector-based semantic search.

            [Goal]
            - Expand the user's original query into a richer representation that includes
              related concepts, synonyms, higher-level categories, and domain-relevant keywords.
            - The expanded query must significantly increase recall in vector search while
              preserving the user’s actual intent.

            [Rules]
            1. Keep the original meaning but expand it with 5–10+ relevant concepts.
            2. Do NOT write sentences. Use comma-separated keywords only.
            3. Prefer noun-centric keywords.
            4. Include synonyms, related terms, and broader/narrower concepts.
            5. English-focused expansion is preferred, but include multilingual keywords
               if they are commonly-used domain terms (optional).
            6. Output MUST be valid JSON.

            [Output Format]
            {
              "original_query": "{{user_query}}",
              "expanded_query": "<comma-separated expanded keywords>",
              "keywords": ["keyword1", "keyword2", ...]
            }

            Rewrite and expand the following user query:

            "{{user_query}}"
                        """;

    private final PromptManager promptManager;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 입력 쿼리를 검색 최적화 형태로 리라이트하는 프롬프트를 생성한다.
     * 실제 모델 호출은 클라이언트 책임이며, 프롬프트 문자열과 가이드를 반환한다.
     */
    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read')")
    public ApiResponse<QueryRewriteResponseDto> rewrite(@Valid @RequestBody QueryRewriteRequestDto request) {
        String prompt = buildRewritePrompt(request.query());
        String raw = callChat(prompt, request.query());
        QueryRewriteResponseDto body = parseRewriteResponse(request.query(), raw, prompt);
        return ApiResponse.ok(body);
    }

    private String buildRewritePrompt(String query) {
        try {
            return promptManager.render(TEMPLATE_NAME, java.util.Map.of("user_query", query));
        } catch (Exception ex) {
            log.warn("Failed to render query rewrite prompt with template '{}', using fallback. cause={}", TEMPLATE_NAME, ex.toString());
            return FALLBACK_PROMPT.replace("{{user_query}}", query);
        }
    }

    private String callChat(String prompt, String originalQuery) {
        ChatRequest request = ChatRequest.builder()
                .messages(java.util.List.of(
                        new ChatMessage(ChatMessageRole.SYSTEM, prompt),
                        new ChatMessage(ChatMessageRole.USER, originalQuery)))
                .build();
        ChatResponse response = chatPort.chat(request);
        return response.messages().get(0).content().trim();
    }

    private QueryRewriteResponseDto parseRewriteResponse(String originalQuery, String raw, String prompt) {
        String cleaned = sanitize(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String expanded = textValue(node.get("expanded_query"), raw);
            java.util.List<String> keywords = toList(node.get("keywords"));
            String orig = textValue(node.get("original_query"), originalQuery);
            return new QueryRewriteResponseDto(orig, expanded, keywords, prompt, raw);
        } catch (Exception e) {
            log.warn("Failed to parse query rewrite response as JSON. Using raw text. cause={}", e.toString());
            return new QueryRewriteResponseDto(originalQuery, cleaned, java.util.List.of(), prompt, raw);
        }
    }

    /**
     * 코드펜스(````, ```json)로 감싼 응답이 오면 제거한다.
     */
    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            // remove leading ```[lang]
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            // remove trailing ```
            int fence = trimmed.lastIndexOf("```");
            if (fence >= 0) {
                trimmed = trimmed.substring(0, fence);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    private String textValue(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        String v = node.asText();
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private java.util.List<String> toList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return java.util.List.of();
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        node.forEach(n -> {
            String v = n.asText();
            if (v != null && !v.isBlank()) {
                list.add(v);
            }
        });
        return list;
    }
}

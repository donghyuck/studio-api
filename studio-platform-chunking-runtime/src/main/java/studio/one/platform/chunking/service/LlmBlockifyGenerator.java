package studio.one.platform.chunking.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.chunking.core.NormalizedBlock;

public class LlmBlockifyGenerator implements BlockifyGenerator {

    private static final int MAX_INPUT_CHARS = 12_000;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };
    private static final Set<String> COMMON_KEYS = Set.of(
            "name", "title", "heading", "sectionTitle", "section_title",
            "question", "criticalQuestion", "critical_question", "query", "userQuestion", "user_question",
            "answer", "trustedAnswer", "trusted_answer", "response", "content", "summary",
            "keywords", "keyword", "searchKeywords", "search_keywords", "tags", "tag",
            "entityName", "entity_name", "entityType", "entity_type",
            "sourceEvidence", "source_evidence", "evidence", "evidences", "citations",
            "sourceBlockRange", "source_block_range", "confidence");

    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    public LlmBlockifyGenerator(AiProviderRegistry providerRegistry) {
        this(providerRegistry, new ObjectMapper());
    }

    LlmBlockifyGenerator(AiProviderRegistry providerRegistry, ObjectMapper objectMapper) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public List<BlockifyBlock> generate(BlockifyGenerationRequest request) {
        ChatPort chatPort = providerRegistry.chatPort(request.llmProvider());
        ChatResponse response = chatPort.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(systemPrompt(request)),
                        ChatMessage.user(userPrompt(request))))
                .model(request.llmModel())
                .temperature(request.temperature())
                .topP(request.topP())
                .maxOutputTokens(estimateMaxOutputTokens(request))
                .build());
        String raw = response.messages().get(response.messages().size() - 1).content();
        return parseBlocks(raw, request);
    }

    private String systemPrompt(BlockifyGenerationRequest request) {
        String common = promptResource("common", fallbackCommonPrompt());
        String typeSpecific = promptResource(promptResourceName(request), typeSpecificSchema(request));
        return common + "\n\nDocument type profile:\n" + typeSpecific;
    }

    private String fallbackCommonPrompt() {
        return """
                You are a Knowledge Block generator for enterprise RAG.
                Convert the source section enclosed in <source_document> tags into one or more grounded IdeaBlocks.
                Use only facts present in the source. Do not infer missing policies.
                Every sourceEvidence.text must be copied verbatim from the source text.
                Avoid generic questions such as "what should be checked about this?".
                Respond with JSON only.
                Schema:
                [
                  {
                    "name": "short stable IdeaBlock name",
                    "criticalQuestion": "specific Korean question answerable by the source",
                    "trustedAnswer": "grounded Korean answer",
                    "keywords": ["keyword"],
                    "tags": ["tag"],
                    "entityName": "article/condition/table row name",
                    "entityType": "article|clause|item|table-row|exception|section",
                    "sourceEvidence": [
                      {"text": "verbatim source excerpt"}
                    ],
                    "sourceBlockRange": {"start": 1, "end": 1},
                    "confidence": 0.0
                  }
                ]
                Generate separate IdeaBlocks for independent clauses, subparagraphs, table rows, exceptions, dates,
                numbers, and conditions. Do not merge blocks when numbers, dates, periods, percentages, or conditions differ.
                """;
    }

    private String promptResourceName(BlockifyGenerationRequest request) {
        BlockifyDocumentType type = request == null || request.documentType() == null
                ? BlockifyDocumentType.GENERAL
                : request.documentType();
        return type.value();
    }

    private String promptResource(String name, String fallback) {
        String resource = "prompts/blockify/" + name + ".v1.prompt";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return fallback;
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isBlank() ? fallback : text;
        } catch (IOException ex) {
            return fallback;
        }
    }

    private String userPrompt(BlockifyGenerationRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("promptVersion: ").append(nullToEmpty(request.promptVersion())).append('\n');
        builder.append("documentType: ").append(request.documentType() == null ? "" : request.documentType().value()).append('\n');
        builder.append("blockifyProfile: ").append(nullToEmpty(request.blockifyProfile())).append('\n');
        builder.append("ideaBlockSchemaVersion: ").append(nullToEmpty(request.ideaBlockSchemaVersion())).append('\n');
        builder.append("sectionId: ").append(nullToEmpty(request.sectionId())).append('\n');
        builder.append("headingPath: ").append(nullToEmpty(request.headingPath())).append('\n');
        builder.append("maxBlocks: ").append(request.maxBlocksPerSection()).append("\n\n");
        builder.append("SOURCE TEXT:\n");
        builder.append("<source_document>\n");
        builder.append(trim(sourceText(request.blocks()), MAX_INPUT_CHARS));
        builder.append("\n</source_document>");
        return builder.toString();
    }

    private List<BlockifyBlock> parseBlocks(String raw, BlockifyGenerationRequest request) {
        String json = extractJson(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = blockArray(root);
            if (!array.isArray()) {
                array = objectMapper.createArrayNode().add(root);
            }
            List<BlockifyBlock> blocks = new ArrayList<>();
            for (JsonNode node : array) {
                if (blocks.size() >= request.maxBlocksPerSection()) {
                    break;
                }
                BlockifyBlock block = toBlock(node, request);
                if (block != null) {
                    blocks.add(block);
                }
            }
            return blocks;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Blockify LLM response", ex);
        }
    }

    private BlockifyBlock toBlock(JsonNode node, BlockifyGenerationRequest request) {
        String name = firstText(node, "name", "title", "heading", "sectionTitle", "section_title");
        String title = firstText(node, "title", "name", "heading", "sectionTitle", "section_title");
        String question = firstText(node, "question", "criticalQuestion", "critical_question",
                "query", "userQuestion", "user_question");
        String answer = firstText(node, "answer", "trustedAnswer", "trusted_answer",
                "response", "content", "summary");
        List<String> keywords = stringList(firstNode(node, "keywords", "keyword", "searchKeywords", "search_keywords"));
        List<String> tags = stringList(firstNode(node, "tags", "tag"));
        String entityName = firstText(node, "entityName", "entity_name");
        String entityType = firstText(node, "entityType", "entity_type");
        List<BlockifySourceEvidence> evidence = evidenceList(firstNode(node,
                "sourceEvidence", "source_evidence", "evidence", "evidences", "citations"), request);
        BlockifyBlock.SourceBlockRange range = sourceBlockRange(firstNode(node, "sourceBlockRange", "source_block_range"));
        double confidence = node.hasNonNull("confidence") ? node.get("confidence").asDouble(0.8d) : 0.8d;
        Map<String, Object> typedFields = typedFields(node);
        if (isBlank(title)) {
            title = request.headingPath();
        }
        return new BlockifyBlock(name, title, question, answer, keywords, tags, entityName, entityType, evidence,
                range, request.sectionId(), confidence, typedFields);
    }

    private String typeSpecificSchema(BlockifyGenerationRequest request) {
        BlockifyDocumentType type = request.documentType() == null ? BlockifyDocumentType.GENERAL : request.documentType();
        return switch (type) {
            case POLICY -> """
                    POLICY schema: add ruleName, articleNo, clauseNo, condition, obligation, prohibition,
                    exception, effectiveScope, penalty, deadline when grounded in sourceEvidence.
                    """;
            case MANUAL -> """
                    MANUAL schema: add taskName, procedureStep, prerequisite, input, output, actor,
                    toolName, warning, nextAction when grounded in sourceEvidence.
                    """;
            case NARRATIVE -> """
                    NARRATIVE schema: add chapter, scene, character, characterRole, event, eventType,
                    motivation, conflict, cause, effect, location, timeReference, quote when grounded in sourceEvidence.
                    The criticalQuestion may be English or Korean, but it must be specific to the source event.
                    """;
            case TECHNICAL -> """
                    TECHNICAL schema: add component, apiName, endpoint, method, parameter, returnValue,
                    errorCode, command, configurationKey, dependency, version when grounded in sourceEvidence.
                    """;
            case TABLE_HEAVY -> """
                    TABLE_HEAVY schema: add tableTitle, rowKey, columnKey, metricName, metricValue,
                    unit, comparisonTarget, condition when grounded in sourceEvidence.
                    """;
            case AUTO, GENERAL -> """
                    GENERAL schema: add topic, summary, mainClaim, supportingEvidence when grounded in sourceEvidence.
                    """;
        };
    }

    private Map<String, Object> typedFields(JsonNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode explicit = firstNode(node, "typedFields", "typed_fields", "metadata");
        if (explicit != null && explicit.isObject()) {
            try {
                values.putAll(objectMapper.convertValue(explicit, OBJECT_MAP));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed typed metadata and keep parsing common IdeaBlock fields.
            }
        }
        node.fields().forEachRemaining(entry -> {
            if (!COMMON_KEYS.contains(entry.getKey()) && !entry.getValue().isNull()) {
                values.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
            }
        });
        values.entrySet().removeIf(entry -> entry.getKey() == null
                || entry.getKey().isBlank()
                || entry.getValue() == null
                || (entry.getValue() instanceof String text && text.isBlank()));
        return Map.copyOf(values);
    }

    private BlockifyBlock.SourceBlockRange sourceBlockRange(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        Integer start = intValue(node, "start");
        Integer end = intValue(node, "end");
        if (start == null && end == null) {
            return null;
        }
        return new BlockifyBlock.SourceBlockRange(start, end == null ? start : end);
    }

    private List<BlockifySourceEvidence> evidenceList(JsonNode node, BlockifyGenerationRequest request) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<BlockifySourceEvidence> evidence = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                addEvidence(evidence, item, request);
            }
        } else {
            addEvidence(evidence, node, request);
        }
        return evidence;
    }

    private void addEvidence(List<BlockifySourceEvidence> evidence, JsonNode node, BlockifyGenerationRequest request) {
        String text = node.isTextual() ? node.asText() : firstText(node,
                "text", "sourceText", "source_text", "quote", "excerpt", "evidenceText", "evidence_text");
        if (isBlank(text)) {
            return;
        }
        evidence.add(new BlockifySourceEvidence(
                text,
                intValue(node, "normalizedBlockIndex"),
                intValue(node, "startOffset"),
                intValue(node, "endOffset"),
                intValue(node, "page"),
                intValue(node, "slide"),
                isBlank(request.headingPath()) ? List.of() : List.of(request.headingPath()),
                request.sectionId(),
                List.of()));
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            if (node.isArray()) {
                return objectMapper.convertValue(node, STRING_LIST).stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
            }
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            return List.of(node.asText().trim());
        }
        return List.of();
    }

    private String sourceText(List<NormalizedBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        return blocks.stream()
                .map(block -> {
                    String text = block.text();
                    if (text == null || text.isBlank()) {
                        return "";
                    }
                    return "[sourceBlock order=%s ref=%s]\n%s".formatted(
                            block.order() == null ? "" : block.order(),
                            block.effectiveSourceRef() == null ? "" : block.effectiveSourceRef(),
                            text);
                })
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Integer intValue(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        JsonNode value = node.get(key);
        return value == null || !value.canConvertToInt() ? null : value.asInt();
    }

    private JsonNode blockArray(JsonNode root) {
        if (root == null || root.isNull() || root.isArray()) {
            return root;
        }
        for (String key : List.of("blocks", "ideaBlocks", "idea_blocks", "knowledgeBlocks",
                "knowledge_blocks", "items", "results")) {
            JsonNode value = root.get(key);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return root;
    }

    private JsonNode firstNode(JsonNode node, String... keys) {
        if (node == null || node.isNull() || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private int estimateMaxOutputTokens(BlockifyGenerationRequest request) {
        return Math.max(512, Math.min(4096, request.maxBlocksPerSection() * 512));
    }

    private String extractJson(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = stripFence(value.trim());
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return trimmed;
        }
        int arrayStart = trimmed.indexOf('[');
        int objectStart = trimmed.indexOf('{');
        int start;
        char open;
        char close;
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            start = arrayStart;
            open = '[';
            close = ']';
        } else {
            start = objectStart;
            open = '{';
            close = '}';
        }
        if (start < 0) {
            return trimmed;
        }
        int end = matchingEnd(trimmed, start, open, close);
        return end < 0 ? trimmed.substring(start).trim() : trimmed.substring(start, end + 1).trim();
    }

    private String stripFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline > 0) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        int fence = trimmed.lastIndexOf("```");
        if (fence >= 0) {
            trimmed = trimmed.substring(0, fence);
        }
        return trimmed.trim();
    }

    private int matchingEnd(String value, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String trim(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}

package studio.one.platform.skillgraph.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.command.PreviewSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryGraphView;
import studio.one.platform.skillgraph.application.result.SkillCategoryParentSuggestionView;
import studio.one.platform.skillgraph.application.result.SkillCategoryRelationView;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryRelationService;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelation;
import studio.one.platform.skillgraph.domain.model.SkillCategoryRelationType;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillCategoryRelationStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

@Slf4j
public class DefaultSkillCategoryRelationService implements SkillCategoryRelationService {

    private static final String CATEGORY_RELATION_PROMPT = "skill-category-relations";

    private final SkillTaxonomyStore taxonomyStore;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillCategoryRelationStore relationStore;
    private final PromptRenderer promptRenderer;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;

    public DefaultSkillCategoryRelationService(
            SkillTaxonomyStore taxonomyStore,
            SkillDictionaryStore dictionaryStore,
            SkillCategoryRelationStore relationStore) {
        this(taxonomyStore, dictionaryStore, relationStore, null, null, null);
    }

    public DefaultSkillCategoryRelationService(
            SkillTaxonomyStore taxonomyStore,
            SkillDictionaryStore dictionaryStore,
            SkillCategoryRelationStore relationStore,
            PromptRenderer promptRenderer,
            ChatPort chatPort,
            ObjectMapper objectMapper) {
        this.taxonomyStore = taxonomyStore;
        this.dictionaryStore = dictionaryStore;
        this.relationStore = relationStore;
        this.promptRenderer = promptRenderer;
        this.chatPort = chatPort;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public SkillCategoryGraphView preview(PreviewSkillCategoryRelationsCommand command) {
        List<String> categoryIds = normalizeIds(command == null ? null : command.categoryIds());
        int representativeLimit = command == null || command.representativeSkillLimit() == null
                ? 10
                : Math.max(1, Math.min(command.representativeSkillLimit(), 100));
        double minScore = command == null || command.minScore() == null ? 0.25d : Math.max(0.0d, command.minScore());
        List<SkillCategory> categories = loadCategories(categoryIds);
        List<SkillDictionary> skills = categories.stream()
                .flatMap(category -> dictionaryStore.findByCategoryId(category.categoryId(), representativeLimit).stream())
                .toList();
        List<SkillCategoryRelationView> relations = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            for (int j = i + 1; j < categories.size(); j++) {
                SkillCategoryRelationView relation = previewRelation(categories.get(i), categories.get(j),
                        representativeLimit, minScore);
                if (relation != null) {
                    relations.add(relation);
                }
            }
        }
        List<SkillCategoryParentSuggestionView> parentSuggestions = List.of();
        if (command != null && Boolean.TRUE.equals(command.useLlm())) {
            LlmCategoryRelationResult llmResult = previewWithLlm(categories, skills, relations);
            if (llmResult != null) {
                relations = new ArrayList<>(llmResult.relations());
                parentSuggestions = llmResult.parentSuggestions();
            }
        }
        if (command == null || Boolean.TRUE.equals(command.includePersisted())) {
            relations.addAll(relationStore.findByCategoryIds(categoryIds).stream()
                    .map(SkillCategoryRelationView::from)
                    .toList());
        }
        return new SkillCategoryGraphView(
                categories.stream().map(SkillCategoryView::from).toList(),
                skills.stream().map(skill -> SkillDictionaryView.from(skill, categoryName(skill.categoryId()))).toList(),
                relations.stream()
                        .sorted(Comparator.comparingDouble(SkillCategoryRelationView::score).reversed())
                        .toList(),
                parentSuggestions);
    }

    @Override
    public List<SkillCategoryRelationView> saveRelations(SaveSkillCategoryRelationsCommand command) {
        if (command == null || command.relations() == null || command.relations().isEmpty()) {
            throw new IllegalArgumentException("relations must not be empty");
        }
        Instant now = Instant.now();
        return command.relations().stream()
                .map(item -> {
                    String sourceCategoryId = required(item.sourceCategoryId(), "sourceCategoryId");
                    String targetCategoryId = required(item.targetCategoryId(), "targetCategoryId");
                    if (sourceCategoryId.equals(targetCategoryId)) {
                        throw new IllegalArgumentException("sourceCategoryId and targetCategoryId must be different");
                    }
                    requireCategory(sourceCategoryId);
                    requireCategory(targetCategoryId);
                    SkillCategoryRelationType relationType = item.relationType() == null
                            ? SkillCategoryRelationType.RELATED
                            : item.relationType();
                    SkillCategoryRelation relation = new SkillCategoryRelation(
                            normalize(item.relationId()) == null
                                    ? stableRelationId(sourceCategoryId, targetCategoryId, relationType)
                                    : item.relationId(),
                            sourceCategoryId,
                            targetCategoryId,
                            relationType,
                            clamp(item.score() == null ? 0.0d : item.score()),
                            clamp(item.confidence() == null ? 0.0d : item.confidence()),
                            normalize(item.reason()),
                            now,
                            now);
                    return SkillCategoryRelationView.from(relationStore.save(relation));
                })
                .toList();
    }

    private String stableRelationId(
            String sourceCategoryId,
            String targetCategoryId,
            SkillCategoryRelationType relationType) {
        String key = sourceCategoryId + ":" + targetCategoryId + ":" + relationType.name();
        return "scr_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    @Override
    public List<SkillCategoryRelationView> findRelations(List<String> categoryIds) {
        return relationStore.findByCategoryIds(normalizeIds(categoryIds)).stream()
                .map(SkillCategoryRelationView::from)
                .toList();
    }

    @Override
    public void deleteRelation(String relationId) {
        relationStore.delete(required(relationId, "relationId"));
    }

    private SkillCategoryRelationView previewRelation(
            SkillCategory left,
            SkillCategory right,
            int representativeLimit,
            double minScore) {
        List<SkillDictionary> leftSkills = dictionaryStore.findByCategoryId(left.categoryId(), representativeLimit);
        List<SkillDictionary> rightSkills = dictionaryStore.findByCategoryId(right.categoryId(), representativeLimit);
        Set<String> leftTokens = tokens(left, leftSkills);
        Set<String> rightTokens = tokens(right, rightSkills);
        double score = jaccard(leftTokens, rightTokens);
        if (isParent(left, right) || isParent(right, left)) {
            score = Math.max(score, 0.95d);
        }
        if (score < minScore) {
            return null;
        }
        SkillCategoryRelationType type = isParent(left, right) || isParent(right, left)
                ? SkillCategoryRelationType.PARENT
                : score >= 0.65d ? SkillCategoryRelationType.OVERLAPS_WITH : SkillCategoryRelationType.RELATED;
        return SkillCategoryRelationView.preview(
                left.categoryId(),
                right.categoryId(),
                type,
                score,
                Math.min(0.95d, 0.5d + score / 2.0d),
                "카테고리명과 대표 스킬의 공통 용어 기반 연관도");
    }

    private LlmCategoryRelationResult previewWithLlm(
            List<SkillCategory> categories,
            List<SkillDictionary> skills,
            List<SkillCategoryRelationView> heuristicRelations) {
        if (promptRenderer == null || chatPort == null || categories.isEmpty()) {
            log.debug("Skipping skill category relation LLM preview: promptRendererPresent={}, chatPortPresent={}, categoryCount={}",
                    promptRenderer != null,
                    chatPort != null,
                    categories.size());
            return null;
        }
        try {
            Map<String, List<SkillDictionary>> skillsByCategory = skills.stream()
                    .collect(java.util.stream.Collectors.groupingBy(SkillDictionary::categoryId));
            List<Map<String, Object>> categoryPayload = categories.stream()
                    .map(category -> Map.<String, Object>of(
                            "categoryId", category.categoryId(),
                            "name", category.name(),
                            "parentCategoryId", category.parentCategoryId() == null ? "" : category.parentCategoryId(),
                            "representativeSkills", skillsByCategory.getOrDefault(category.categoryId(), List.of()).stream()
                                    .limit(12)
                                    .map(skill -> Map.<String, Object>of(
                                            "skillId", skill.skillId(),
                                            "skillName", skill.name(),
                                            "normalizedName", skill.normalizedName()))
                                    .toList()))
                    .toList();
            List<Map<String, Object>> relationPayload = heuristicRelations.stream()
                    .limit(80)
                    .map(relation -> Map.<String, Object>of(
                            "sourceCategoryId", relation.sourceCategoryId(),
                            "targetCategoryId", relation.targetCategoryId(),
                            "relationType", relation.relationType().name(),
                            "score", relation.score(),
                            "confidence", relation.confidence(),
                            "reason", relation.reason() == null ? "" : relation.reason()))
                    .toList();
            String prompt = promptRenderer.render(CATEGORY_RELATION_PROMPT, Map.of(
                    "categoriesJson", writeJson(categoryPayload),
                    "heuristicRelationsJson", writeJson(relationPayload)));
            ChatResponse response = chatPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .maxOutputTokens(2048)
                    .temperature(0.0d)
                    .build());
            String raw = lastAssistantContent(response);
            LlmCategoryRelationResult parsed = parseLlmResult(raw, categories);
            if (parsed == null) {
                log.warn("Skill category relation LLM response was not usable; falling back to heuristic: responseChars={}, preview={}",
                        raw == null ? 0 : raw.length(),
                        preview(raw));
            }
            return parsed;
        } catch (RuntimeException ex) {
            log.warn("Failed to preview skill category relations via LLM: {}", ex.toString());
            return null;
        }
    }

    private LlmCategoryRelationResult parseLlmResult(String raw, List<SkillCategory> categories) {
        String value = stripFence(raw == null ? "" : raw);
        if (value.isBlank()) {
            return null;
        }
        Set<String> categoryIds = categories.stream()
                .map(SkillCategory::categoryId)
                .collect(java.util.stream.Collectors.toSet());
        try {
            JsonNode root = objectMapper.readTree(value);
            List<SkillCategoryRelationView> relations = new ArrayList<>();
            JsonNode relationNodes = root.path("relations");
            if (relationNodes.isArray()) {
                for (JsonNode node : relationNodes) {
                    String source = normalize(node.path("sourceCategoryId").asText(null));
                    String target = normalize(node.path("targetCategoryId").asText(null));
                    if (source == null || target == null || source.equals(target)
                            || !categoryIds.contains(source) || !categoryIds.contains(target)) {
                        continue;
                    }
                    SkillCategoryRelationType type = parseRelationType(node.path("relationType").asText(null));
                    relations.add(SkillCategoryRelationView.preview(
                            source,
                            target,
                            type,
                            clamp(node.path("score").asDouble(0.0d)),
                            clamp(node.path("confidence").asDouble(0.75d)),
                            normalize(node.path("reason").asText(null))));
                }
            }
            List<SkillCategoryParentSuggestionView> parentSuggestions = new ArrayList<>();
            JsonNode suggestionNodes = root.path("parentSuggestions");
            if (suggestionNodes.isArray()) {
                for (JsonNode node : suggestionNodes) {
                    String suggestedName = normalize(node.path("suggestedName").asText(null));
                    JsonNode childNodes = node.path("childCategoryIds");
                    if (suggestedName == null || !childNodes.isArray()) {
                        continue;
                    }
                    List<String> childCategoryIds = new ArrayList<>();
                    childNodes.forEach(child -> {
                        String childId = normalize(child.asText(null));
                        if (childId != null && categoryIds.contains(childId) && !childCategoryIds.contains(childId)) {
                            childCategoryIds.add(childId);
                        }
                    });
                    if (childCategoryIds.size() < 2) {
                        continue;
                    }
                    parentSuggestions.add(new SkillCategoryParentSuggestionView(
                            normalize(node.path("suggestionId").asText(null)) == null
                                    ? String.join("__", childCategoryIds)
                                    : normalize(node.path("suggestionId").asText(null)),
                            suggestedName,
                            childCategoryIds,
                            Math.max(0, node.path("relationCount").asInt(childCategoryIds.size() - 1)),
                            clamp(node.path("score").asDouble(0.0d)),
                            clamp(node.path("confidence").asDouble(0.75d)),
                            normalize(node.path("reason").asText(null))));
                }
            }
            if (relations.isEmpty() && parentSuggestions.isEmpty()) {
                return null;
            }
            return new LlmCategoryRelationResult(relations, parentSuggestions);
        } catch (Exception ex) {
            log.warn("Failed to parse skill category relation LLM response: {}", ex.toString());
            return null;
        }
    }

    private SkillCategoryRelationType parseRelationType(String value) {
        if (value == null || value.isBlank()) {
            return SkillCategoryRelationType.RELATED;
        }
        try {
            return SkillCategoryRelationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SkillCategoryRelationType.RELATED;
        }
    }

    private List<SkillCategory> loadCategories(List<String> categoryIds) {
        if (categoryIds.isEmpty()) {
            return taxonomyStore.searchCategories(null, null, org.springframework.data.domain.Pageable.ofSize(200))
                    .getContent();
        }
        return categoryIds.stream()
                .map(this::requireCategory)
                .toList();
    }

    private SkillCategory requireCategory(String categoryId) {
        String id = required(categoryId, "categoryId");
        return taxonomyStore.findCategory(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + id));
    }

    private String categoryName(String categoryId) {
        return categoryId == null ? null : taxonomyStore.findCategory(categoryId)
                .map(SkillCategory::name)
                .orElse(null);
    }

    private Set<String> tokens(SkillCategory category, List<SkillDictionary> skills) {
        Set<String> values = new LinkedHashSet<>();
        addTokens(values, category.categoryId());
        addTokens(values, category.name());
        for (SkillDictionary skill : skills) {
            addTokens(values, skill.name());
            addTokens(values, skill.normalizedName());
        }
        return values;
    }

    private void addTokens(Set<String> tokens, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        long intersection = left.stream().filter(right::contains).count();
        long union = java.util.stream.Stream.concat(left.stream(), right.stream()).distinct().count();
        return union == 0 ? 0.0d : intersection / (double) union;
    }

    private boolean isParent(SkillCategory maybeParent, SkillCategory maybeChild) {
        return maybeParent.categoryId().equals(maybeChild.parentCategoryId());
    }

    private List<String> normalizeIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String lastAssistantContent(ChatResponse response) {
        if (response == null || response.messages().isEmpty()) {
            return "";
        }
        return response.messages().get(response.messages().size() - 1).content();
    }

    private String stripFence(String value) {
        String stripped = value == null ? "" : value.strip();
        if (!stripped.startsWith("```")) {
            return stripped;
        }
        stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").strip();
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize skill category relation prompt input", ex);
        }
    }

    private record LlmCategoryRelationResult(
            List<SkillCategoryRelationView> relations,
            List<SkillCategoryParentSuggestionView> parentSuggestions) {
    }
}

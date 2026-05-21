package studio.one.platform.skillgraph.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.chat.ChatRequest;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.service.prompt.PromptRenderer;
import studio.one.platform.skillgraph.application.command.GenerateSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveAndAssignSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftAssignmentResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftView;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.result.SkillClusterRepresentativeView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStats;
import studio.one.platform.skillgraph.domain.model.SkillCategory;
import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;
import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillProjectionStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;

@Slf4j
public class DefaultSkillCategoryDraftService implements SkillCategoryDraftService {

    private static final String NOISE_CLUSTER_ID = "noise";
    private static final String CATEGORY_NAMING_PROMPT = "skill-category-naming";
    private static final Pattern SUGGESTED_CATEGORY_NAME_PATTERN = Pattern.compile(
            "\"suggestedCategoryName\"\\s*:\\s*\"([^\"]+)\"");

    private final SkillProjectionStore projectionStore;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillTaxonomyStore taxonomyStore;
    private final SkillCandidateStore candidateStore;
    private final PromptRenderer promptRenderer;
    private final ChatPort chatPort;
    private final ObjectMapper objectMapper;

    public DefaultSkillCategoryDraftService(
            SkillProjectionStore projectionStore,
            SkillDictionaryStore dictionaryStore,
            SkillTaxonomyStore taxonomyStore,
            SkillCandidateStore candidateStore,
            PromptRenderer promptRenderer,
            ChatPort chatPort,
            ObjectMapper objectMapper) {
        this.projectionStore = projectionStore;
        this.dictionaryStore = dictionaryStore;
        this.taxonomyStore = taxonomyStore;
        this.candidateStore = candidateStore;
        this.promptRenderer = promptRenderer;
        this.chatPort = chatPort;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public DefaultSkillCategoryDraftService(
            SkillProjectionStore projectionStore,
            SkillDictionaryStore dictionaryStore,
            SkillTaxonomyStore taxonomyStore,
            SkillCandidateStore candidateStore) {
        this(projectionStore, dictionaryStore, taxonomyStore, candidateStore, null, null, null);
    }

    public DefaultSkillCategoryDraftService(
            SkillProjectionStore projectionStore,
            SkillDictionaryStore dictionaryStore,
            SkillTaxonomyStore taxonomyStore) {
        this(projectionStore, dictionaryStore, taxonomyStore, null, null, null, null);
    }

    @Override
    public SkillCategoryDraftResult generateDrafts(String projectionId, int representativeLimit) {
        return generateDrafts(new GenerateSkillCategoryDraftCommand(projectionId, null, representativeLimit, false,
                false));
    }

    @Override
    public SkillCategoryDraftResult generateDrafts(GenerateSkillCategoryDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String projectionId = required(command.projectionId(), "projectionId");
        int representativeLimit = command.representativeLimit() == null ? 10 : command.representativeLimit();
        boolean includeNoise = Boolean.TRUE.equals(command.includeNoise());
        boolean useLlm = Boolean.TRUE.equals(command.useLlm());
        List<String> clusterIds = command.clusterIds() == null ? List.of() : command.clusterIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        return generateDrafts(projectionId, clusterIds, representativeLimit, includeNoise, useLlm);
    }

    public Page<SkillClusterRepresentativeView> findRepresentatives(
            String projectionId,
            String clusterId,
            boolean includeNoise,
            Pageable pageable) {
        String resolvedProjectionId = required(projectionId, "projectionId");
        String resolvedClusterId = required(clusterId, "clusterId");
        if (!includeNoise && isNoise(resolvedClusterId)) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<SkillProjection> points = projectionStore.findProjectionPoints(
                resolvedProjectionId,
                resolvedClusterId,
                PageRequest.of(0, SkillGraphLimits.MAX_PROJECTION_ITEMS))
                .getContent();
        List<SkillClusterRepresentativeView> representatives = representatives(points, pageable.getSort());
        int start = Math.toIntExact(Math.min(pageable.getOffset(), representatives.size()));
        int end = Math.min(start + pageable.getPageSize(), representatives.size());
        return new PageImpl<>(representatives.subList(start, end), pageable, representatives.size());
    }

    private SkillCategoryDraftResult generateDrafts(
            String projectionId,
            List<String> clusterIds,
            int representativeLimit,
            boolean includeNoise,
            boolean useLlm) {
        String resolvedProjectionId = required(projectionId, "projectionId");
        int maxRepresentatives = normalizeRepresentativeLimit(representativeLimit);
        List<SkillCluster> clusters = projectionStore.findClusters(resolvedProjectionId);
        if (!clusterIds.isEmpty()) {
            clusters = clusters.stream()
                    .filter(cluster -> clusterIds.contains(cluster.clusterId()))
                    .toList();
        }
        List<SkillCategoryDraftView> drafts = clusters.stream()
                .filter(cluster -> includeNoise || !isNoise(cluster))
                .sorted(Comparator.comparing(SkillCluster::clusterId))
                .map(cluster -> draft(resolvedProjectionId, cluster, maxRepresentatives, includeNoise, useLlm))
                .toList();
        int noiseCount = (int) drafts.stream().filter(SkillCategoryDraftView::noise).count();
        return new SkillCategoryDraftResult(resolvedProjectionId, drafts.size(), noiseCount, drafts);
    }

    @Override
    public List<SkillCategoryView> saveDrafts(SaveSkillCategoryDraftCommand command) {
        if (command == null || command.categories() == null || command.categories().isEmpty()) {
            throw new IllegalArgumentException("categories must not be empty");
        }
        return command.categories().stream()
                .map(item -> new SkillCategory(
                        normalize(item.categoryId()) == null ? "cat_" + UUID.randomUUID().toString().replace("-", "") : item.categoryId(),
                        normalize(item.parentCategoryId()),
                        required(item.name(), "name"),
                        item.displayOrder() == null ? 0 : item.displayOrder()))
                .map(taxonomyStore::saveCategory)
                .map(SkillCategoryView::from)
                .toList();
    }

    @Override
    public SkillCategoryDraftAssignmentResult saveAndAssignDrafts(SaveAndAssignSkillCategoryDraftCommand command) {
        if (command == null || command.drafts() == null || command.drafts().isEmpty()) {
            throw new IllegalArgumentException("drafts must not be empty");
        }
        String projectionId = required(command.projectionId(), "projectionId");
        boolean includeNoise = Boolean.TRUE.equals(command.includeNoise());
        List<SkillCategoryDraftAssignmentResult.SkillCategoryDraftAssignmentItem> results = command.drafts().stream()
                .filter(Objects::nonNull)
                .map(item -> saveAndAssignDraft(projectionId, includeNoise, item))
                .toList();
        int assignedSkillCount = results.stream()
                .mapToInt(SkillCategoryDraftAssignmentResult.SkillCategoryDraftAssignmentItem::assignedCount)
                .sum();
        return new SkillCategoryDraftAssignmentResult(
                projectionId,
                results.size(),
                assignedSkillCount,
                results);
    }

    private SkillCategoryDraftAssignmentResult.SkillCategoryDraftAssignmentItem saveAndAssignDraft(
            String projectionId,
            boolean includeNoise,
            SaveAndAssignSkillCategoryDraftCommand.SaveAndAssignSkillCategoryDraftItem item) {
        String clusterId = required(item.clusterId(), "clusterId");
        String categoryId = normalize(item.categoryId()) == null
                ? "cat_" + UUID.randomUUID().toString().replace("-", "")
                : item.categoryId();
        SkillCategory category = taxonomyStore.saveCategory(new SkillCategory(
                categoryId,
                normalize(item.parentCategoryId()),
                required(item.name(), "name"),
                item.displayOrder() == null ? 0 : item.displayOrder()));
        saveHistory(category.categoryId(), null, "SAVE_DRAFT", null, category.categoryId(), category.name());
        if (!includeNoise && isNoise(clusterId)) {
            return new SkillCategoryDraftAssignmentResult.SkillCategoryDraftAssignmentItem(
                    clusterId,
                    category.categoryId(),
                    category.name(),
                    true,
                    0);
        }
        List<String> skillIds = projectionStore.findProjectionPoints(
                projectionId,
                clusterId,
                PageRequest.of(0, SkillGraphLimits.MAX_PROJECTION_ITEMS))
                .getContent()
                .stream()
                .map(SkillProjection::skillId)
                .distinct()
                .toList();
        int assignedCount = assignSkillsToCategory(category.categoryId(), skillIds);
        return new SkillCategoryDraftAssignmentResult.SkillCategoryDraftAssignmentItem(
                clusterId,
                category.categoryId(),
                category.name(),
                true,
                assignedCount);
    }

    private int assignSkillsToCategory(String categoryId, List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return 0;
        }
        Map<String, String> previousCategoryIds = new java.util.LinkedHashMap<>();
        for (String skillId : skillIds) {
            previousCategoryIds.put(skillId, dictionaryStore.findById(skillId)
                    .map(SkillDictionary::categoryId)
                    .orElse(null));
        }
        int affected = dictionaryStore.updateCategory(skillIds, categoryId);
        for (String skillId : skillIds) {
            saveHistory(categoryId, skillId, "ASSIGN_SKILL_FROM_DRAFT", previousCategoryIds.get(skillId),
                    categoryId, null);
        }
        return affected;
    }

    private void saveHistory(
            String categoryId,
            String skillId,
            String actionType,
            String previousCategoryId,
            String newCategoryId,
            String detail) {
        taxonomyStore.saveHistory(new SkillCategoryHistory(
                "sch_" + UUID.randomUUID().toString().replace("-", ""),
                categoryId,
                skillId,
                actionType,
                previousCategoryId,
                newCategoryId,
                detail,
                java.time.Instant.now()));
    }

    private SkillCategoryDraftView draft(
            String projectionId,
            SkillCluster cluster,
            int representativeLimit,
            boolean includeNoise,
            boolean useLlm) {
        List<SkillClusterRepresentativeView> representatives = findRepresentatives(
                projectionId,
                cluster.clusterId(),
                includeNoise,
                PageRequest.of(0, representativeLimit, Sort.by(Sort.Order.desc("representativeScore"))))
                .getContent();
        List<String> skillIds = representatives.stream().map(SkillClusterRepresentativeView::skillId).toList();
        List<String> names = representatives.stream()
                .map(SkillClusterRepresentativeView::skillName)
                .toList();
        boolean noise = isNoise(cluster);
        String suggestedName = noise ? "미분류 스킬" : proposeName(names, cluster, representatives, useLlm);
        return new SkillCategoryDraftView(
                "draft_" + cluster.clusterId(),
                cluster.clusterId(),
                suggestedName,
                suggestedName,
                noise ? 0.2d : confidence(cluster.itemCount()),
                noise,
                cluster.itemCount(),
                skillIds,
                names,
                representatives);
    }

    private List<SkillClusterRepresentativeView> representatives(
            List<SkillProjection> points,
            Sort sort) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        double centroidX = points.stream().mapToDouble(SkillProjection::x).average().orElse(0.0d);
        double centroidY = points.stream().mapToDouble(SkillProjection::y).average().orElse(0.0d);
        List<String> skillIds = points.stream().map(SkillProjection::skillId).toList();
        Map<String, SkillCandidateStats> statsBySkillId = candidateStore == null ? Map.of()
                : candidateStore.findCandidateStatsBySkillIds(skillIds).stream()
                        .filter(stats -> stats.skillId() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                SkillCandidateStats::skillId,
                                Function.identity(),
                                (left, right) -> left));
        int maxOccurrence = Math.max(1, statsBySkillId.values().stream()
                .mapToInt(SkillCandidateStats::occurrenceCount)
                .max()
                .orElse(0));
        Comparator<SkillClusterRepresentativeView> comparator = representativeComparator(sort);
        return points.stream()
                .map(point -> representative(point, centroidX, centroidY, statsBySkillId.get(point.skillId()),
                        maxOccurrence))
                .sorted(comparator)
                .toList();
    }

    private SkillClusterRepresentativeView representative(
            SkillProjection point,
            double centroidX,
            double centroidY,
            SkillCandidateStats stats,
            int maxOccurrence) {
        SkillDictionary skill = dictionaryStore.findById(point.skillId())
                .orElse(new SkillDictionary(point.skillId(), point.skillId(), point.skillId(), null, "UNKNOWN",
                        point.createdAt(), point.createdAt()));
        double distance = Math.hypot(point.x() - centroidX, point.y() - centroidY);
        int occurrenceCount = stats == null ? 0 : stats.occurrenceCount();
        double confidenceScore = stats == null ? 0.0d : stats.confidenceScore();
        double proximity = 1.0d / (1.0d + distance);
        double representativeScore = (proximity * 0.6d)
                + ((occurrenceCount / (double) maxOccurrence) * 0.25d)
                + (confidenceScore * 0.15d);
        return new SkillClusterRepresentativeView(
                point.skillId(),
                skill.name(),
                skill.normalizedName(),
                point.clusterId(),
                point.x(),
                point.y(),
                distance,
                occurrenceCount,
                confidenceScore,
                skill.categoryId(),
                skill.status(),
                representativeScore);
    }

    private Comparator<SkillClusterRepresentativeView> representativeComparator(Sort sort) {
        Sort.Order order = sort == null || sort.isUnsorted()
                ? Sort.Order.desc("representativeScore")
                : sort.iterator().next();
        Comparator<SkillClusterRepresentativeView> comparator = switch (order.getProperty()) {
            case "centroidDistance" -> Comparator.comparingDouble(SkillClusterRepresentativeView::centroidDistance);
            case "occurrenceCount" -> Comparator.comparingInt(
                    view -> view.occurrenceCount() == null ? 0 : view.occurrenceCount());
            case "confidenceScore" -> Comparator.comparingDouble(
                    view -> view.confidenceScore() == null ? 0.0d : view.confidenceScore());
            case "representativeScore" -> Comparator.comparingDouble(SkillClusterRepresentativeView::representativeScore);
            default -> Comparator.comparingDouble(SkillClusterRepresentativeView::representativeScore);
        };
        if (order.isDescending()) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(SkillClusterRepresentativeView::skillName);
    }

    private String proposeName(
            List<String> names,
            SkillCluster cluster,
            List<SkillClusterRepresentativeView> representatives,
            boolean useLlm) {
        if (useLlm) {
            String suggested = proposeNameWithLlm(representatives);
            if (suggested != null) {
                return suggested;
            }
        }
        String joined = String.join(" ", names).toLowerCase(Locale.ROOT);
        if (joined.contains("security") || joined.contains("인증") || joined.contains("인가") || joined.contains("oauth")) {
            return "인증·인가 보안";
        }
        if (joined.contains("vue") || joined.contains("react") || joined.contains("frontend") || joined.contains("컴포넌트")) {
            return "프론트엔드 UI 개발";
        }
        if (joined.contains("postgresql") || joined.contains("데이터") || joined.contains("jpa") || joined.contains("hibernate")) {
            return "데이터 처리 및 저장";
        }
        if (joined.contains("docker") || joined.contains("kubernetes") || joined.contains("배포") || joined.contains("운영")) {
            return "배포 및 운영 자동화";
        }
        if (joined.contains("api") || joined.contains("spring") || joined.contains("backend")) {
            return "백엔드 API 개발";
        }
        return cluster.label() == null ? "스킬 카테고리 " + cluster.clusterId() : cluster.label();
    }

    private String proposeNameWithLlm(List<SkillClusterRepresentativeView> representatives) {
        if (promptRenderer == null || chatPort == null || representatives == null || representatives.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping skill category LLM naming: promptRendererPresent={}, chatPortPresent={}, representativeCount={}",
                        promptRenderer != null,
                        chatPort != null,
                        representatives == null ? 0 : representatives.size());
            }
            return null;
        }
        try {
            String clusterId = representatives.get(0).clusterId();
            log.debug("Requesting skill category LLM naming: clusterId={}, representativeCount={}",
                    clusterId,
                    representatives.size());
            List<Map<String, Object>> skills = representatives.stream()
                    .map(skill -> Map.<String, Object>of(
                            "skillName", skill.skillName(),
                            "normalizedName", skill.normalizedName(),
                            "centroidDistance", skill.centroidDistance(),
                            "occurrenceCount", skill.occurrenceCount() == null ? 0 : skill.occurrenceCount(),
                            "confidenceScore", skill.confidenceScore() == null ? 0.0d : skill.confidenceScore()))
                    .toList();
            String prompt = promptRenderer.render(CATEGORY_NAMING_PROMPT, Map.of(
                    "skills", skills,
                    "skillNames", representatives.stream()
                            .map(SkillClusterRepresentativeView::skillName)
                            .toList()));
            ChatResponse response = chatPort.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .maxOutputTokens(512)
                    .temperature(0.0d)
                    .build());
            String raw = lastAssistantContent(response);
            Object finishReason = response.metadata() == null ? null : response.metadata().get("finishReason");
            log.debug("Received skill category LLM naming response: clusterId={}, finishReason={}, responseChars={}, preview={}",
                    clusterId,
                    finishReason,
                    raw == null ? 0 : raw.length(),
                    preview(raw));
            String suggestedName = parseCategoryName(raw);
            if (suggestedName == null) {
                log.warn("Skill category LLM naming response was not usable; falling back to heuristic: clusterId={}, responseChars={}, preview={}",
                        clusterId,
                        raw == null ? 0 : raw.length(),
                        preview(raw));
            } else {
                log.debug("Parsed skill category LLM naming response: clusterId={}, suggestedName={}",
                        clusterId,
                        suggestedName);
            }
            return suggestedName;
        } catch (RuntimeException ex) {
            log.warn("Failed to suggest skill category name via LLM: {}", ex.toString());
            return null;
        }
    }

    private String parseCategoryName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            String stripped = stripFence(value);
            JsonNode node = objectMapper.readTree(stripped);
            if (node.isTextual()) {
                return normalizeSuggestedName(node.asText());
            }
            if (node.hasNonNull("suggestedCategoryName")) {
                return normalizeSuggestedName(node.get("suggestedCategoryName").asText());
            }
            if (node.hasNonNull("name")) {
                return normalizeSuggestedName(node.get("name").asText());
            }
        } catch (Exception ignored) {
            String extracted = extractSuggestedCategoryName(value);
            if (extracted != null) {
                return extracted;
            }
            if (looksLikeJson(value)) {
                return null;
            }
            return normalizeSuggestedName(value);
        }
        return null;
    }

    private String extractSuggestedCategoryName(String value) {
        Matcher matcher = SUGGESTED_CATEGORY_NAME_PATTERN.matcher(stripFence(value));
        if (!matcher.find()) {
            return null;
        }
        return normalizeSuggestedName(matcher.group(1));
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 160) {
            return normalized.substring(0, 160) + "...";
        }
        return normalized;
    }

    private boolean looksLikeJson(String value) {
        String stripped = stripFence(value).stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    private String lastAssistantContent(ChatResponse response) {
        if (response == null || response.messages().isEmpty()) {
            return "";
        }
        return response.messages().get(response.messages().size() - 1).content();
    }

    private String stripFence(String value) {
        String stripped = value.strip();
        if (!stripped.startsWith("```")) {
            return stripped;
        }
        stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").strip();
    }

    private String normalizeSuggestedName(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace("\"", "").trim();
        if (normalized.length() > 80) {
            return normalized.substring(0, 80).trim();
        }
        return normalized;
    }

    private boolean isNoise(SkillCluster cluster) {
        return isNoise(cluster.clusterId());
    }

    private boolean isNoise(String clusterId) {
        return NOISE_CLUSTER_ID.equalsIgnoreCase(clusterId)
                || clusterId.toLowerCase(Locale.ROOT).contains("noise");
    }

    private int normalizeRepresentativeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 100);
    }

    private double confidence(int itemCount) {
        return Math.min(0.95d, 0.55d + Math.min(itemCount, 40) / 100.0d);
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
}

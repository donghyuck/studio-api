package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.service.pipeline.RagDocumentMetadataProvider;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.chunking.core.ChunkMetadata;

/**
 * Retrieval strategy coordinator for RAG chat.
 */
public class RagChatRetrievalService {

    private static final String STRATEGY_DEFAULT = "default";
    private static final String STRATEGY_STRUCTURE = "structure";
    private static final String STRATEGY_IDEA_BLOCK = "ideaBlock";
    private static final String STRATEGY_HYBRID = "hybrid";
    private static final String STRATEGY_AUTO = "auto";

    private static final String KEY_ACTUAL_CHUNKING_STRATEGY = "actualChunkingStrategy";
    private static final String KEY_DOCUMENT_CHUNK_ID = "documentChunkId";
    private static final String KEY_VECTOR_DOCUMENT_CHUNK_ID = "_documentChunkId";
    private static final String VALUE_STRUCTURE_BASED = "structure-based";
    private static final String VALUE_BLOCKIFY = "blockify";
    private static final String VALUE_IDEA_BLOCK = "ideaBlock";
    private static final int STRATEGY_SAMPLE_LIMIT = 32;

    private final RagPipelineService ragPipelineService;
    private final AiWebRagProperties.RetrievalProperties properties;
    private final List<RagDocumentMetadataProvider> metadataProviders;

    public RagChatRetrievalService(RagPipelineService ragPipelineService) {
        this(ragPipelineService, new AiWebRagProperties.RetrievalProperties());
    }

    public RagChatRetrievalService(
            RagPipelineService ragPipelineService,
            AiWebRagProperties.RetrievalProperties properties) {
        this(ragPipelineService, properties, List.of());
    }

    public RagChatRetrievalService(
            RagPipelineService ragPipelineService,
            AiWebRagProperties.RetrievalProperties properties,
            List<RagDocumentMetadataProvider> metadataProviders) {
        this.ragPipelineService = Objects.requireNonNull(ragPipelineService, "ragPipelineService");
        this.properties = properties == null ? new AiWebRagProperties.RetrievalProperties() : properties;
        this.metadataProviders = metadataProviders == null ? List.of() : List.copyOf(metadataProviders);
    }

    public RetrievalResult retrieve(
            ChatRagRequestDto request,
            String resolvedQuery,
            String objectType,
            String objectId,
            int defaultTopK,
            double defaultMinScore,
            Integer requestedTopK,
            boolean exposeDiagnostics) {
        return retrieve(request, resolvedQuery, objectType, objectId, defaultTopK, defaultMinScore,
                requestedTopK, exposeDiagnostics, false);
    }

    public RetrievalResult retrieve(
            ChatRagRequestDto request,
            String resolvedQuery,
            String objectType,
            String objectId,
            int defaultTopK,
            double defaultMinScore,
            Integer requestedTopK,
            boolean exposeDiagnostics,
            boolean metadataQuery) {
        if (metadataQuery && objectType != null && objectId != null) {
            List<RagSearchResult> metadata = metadataProviders.stream()
                    .filter(provider -> provider.supports(objectType))
                    .flatMap(provider -> provider.find(objectType, objectId).stream())
                    .limit(Math.max(1, defaultTopK))
                    .toList();
            return new RetrievalResult(metadata, RetrievalDebug.disabled());
        }
        RetrievalPlan plan = RetrievalPlan.from(request, properties, defaultTopK, defaultMinScore);
        MetadataFilter baseFilter = baseFilter(objectType, objectId);
        Strategy requestedStrategy = Strategy.from(plan.requestedStrategy());
        RetrievalResolution resolution = resolveStrategy(request, requestedStrategy, objectType, objectId);
        Strategy resolvedStrategy = resolution.strategy();
        request = alignEmbeddingSelection(
                request,
                resolution.embeddingDeploymentId(),
                resolution.embeddingProfileId());
        if (resolvedStrategy == Strategy.DEFAULT) {
            List<RagSearchResult> results = search(request, resolvedQuery, defaultTopK, baseFilter,
                    defaultMinScore, requestedTopK, requestedMinScore(request));
            return new RetrievalResult(limit(results, defaultTopK), RetrievalDebug.disabled());
        }

        List<LegResult> legs = switch (resolvedStrategy) {
            case STRUCTURE -> List.of(searchLeg("structure", request, resolvedQuery, plan.structureTopK(),
                    withEquals(baseFilter, ChunkMetadata.KEY_STRATEGY, VALUE_STRUCTURE_BASED),
                    plan.minScore(), requestedTopK));
            case IDEA_BLOCK -> ideaBlockLegs(request, resolvedQuery, baseFilter, plan, requestedTopK);
            case HYBRID -> {
                List<LegResult> values = new ArrayList<>();
                values.add(searchLeg("structure", request, resolvedQuery, plan.structureTopK(),
                        withEquals(baseFilter, ChunkMetadata.KEY_STRATEGY, VALUE_STRUCTURE_BASED),
                        plan.minScore(), requestedTopK));
                values.addAll(ideaBlockLegs(request, resolvedQuery, baseFilter, plan, requestedTopK));
                yield values;
            }
            case DEFAULT, AUTO -> List.of();
        };
        List<RagSearchResult> merged = merge(legs, plan.finalTopK(), plan.dedupe(), plan.distilledScoreBoost());
        RetrievalDebug debug = exposeDiagnostics
                ? new RetrievalDebug(
                        requestedStrategy.value,
                        resolvedStrategy.value,
                        legs,
                        merged,
                        Boolean.TRUE.equals(request.retrievalOptions() == null
                                ? null : request.retrievalOptions().includeDebugChunks()))
                : RetrievalDebug.disabled();
        return new RetrievalResult(merged, debug);
    }

    private RetrievalResolution resolveStrategy(
            ChatRagRequestDto request,
            Strategy requestedStrategy,
            String objectType,
            String objectId) {
        boolean adaptive = requestedStrategy == Strategy.AUTO
                || request.retrievalStrategy() == null
                || request.retrievalStrategy().isBlank();
        if (objectType == null || objectId == null) {
            return new RetrievalResolution(
                    requestedStrategy == Strategy.AUTO ? Strategy.HYBRID : requestedStrategy,
                    null,
                    null);
        }
        List<RagSearchResult> samples = ragPipelineService.listByObject(objectType, objectId, STRATEGY_SAMPLE_LIMIT);
        boolean structure = false;
        boolean ideaBlock = false;
        String indexedEmbeddingDeploymentId = null;
        boolean mixedEmbeddingDeployments = false;
        String indexedEmbeddingProfileId = null;
        boolean mixedEmbeddingProfiles = false;
        List<RagSearchResult> availableSamples = samples == null ? List.of() : samples;
        for (RagSearchResult sample : availableSamples) {
            Map<String, Object> metadata = sample.metadata() == null ? Map.of() : sample.metadata();
            String strategy = firstText(metadata, KEY_ACTUAL_CHUNKING_STRATEGY, ChunkMetadata.KEY_STRATEGY, "strategy");
            String chunkType = firstText(metadata, ChunkMetadata.KEY_CHUNK_TYPE, "chunkType");
            structure |= VALUE_STRUCTURE_BASED.equalsIgnoreCase(strategy);
            ideaBlock |= VALUE_BLOCKIFY.equalsIgnoreCase(strategy) || VALUE_IDEA_BLOCK.equalsIgnoreCase(chunkType);
            String deploymentId = firstText(metadata, VectorRecord.KEY_EMBEDDING_DEPLOYMENT_ID);
            if (deploymentId != null && indexedEmbeddingDeploymentId == null) {
                indexedEmbeddingDeploymentId = deploymentId;
            } else if (deploymentId != null
                    && !deploymentId.equalsIgnoreCase(indexedEmbeddingDeploymentId)) {
                mixedEmbeddingDeployments = true;
            }
            String profileId = firstText(metadata, VectorRecord.KEY_EMBEDDING_PROFILE_ID);
            if (profileId != null && indexedEmbeddingProfileId == null) {
                indexedEmbeddingProfileId = profileId;
            } else if (profileId != null && !profileId.equalsIgnoreCase(indexedEmbeddingProfileId)) {
                mixedEmbeddingProfiles = true;
            }
        }
        String effectiveDeploymentId = mixedEmbeddingDeployments ? null : indexedEmbeddingDeploymentId;
        String effectiveProfileId = mixedEmbeddingProfiles ? null : indexedEmbeddingProfileId;
        if (!adaptive) {
            return new RetrievalResolution(requestedStrategy, effectiveDeploymentId, effectiveProfileId);
        }
        if (structure && !ideaBlock) {
            return new RetrievalResolution(Strategy.STRUCTURE, effectiveDeploymentId, effectiveProfileId);
        }
        if (ideaBlock && !structure) {
            return new RetrievalResolution(Strategy.IDEA_BLOCK, effectiveDeploymentId, effectiveProfileId);
        }
        if (!structure && !ideaBlock && !availableSamples.isEmpty()) {
            return new RetrievalResolution(Strategy.DEFAULT, effectiveDeploymentId, effectiveProfileId);
        }
        return new RetrievalResolution(Strategy.HYBRID, effectiveDeploymentId, effectiveProfileId);
    }

    private ChatRagRequestDto alignEmbeddingSelection(
            ChatRagRequestDto request,
            String indexedDeploymentId,
            String indexedProfileId) {
        if (hasExplicitEmbeddingSelection(request)) {
            return request;
        }
        String deploymentId = normalize(indexedDeploymentId);
        String profileId = deploymentId == null ? normalize(indexedProfileId) : null;
        if (deploymentId == null && profileId == null) {
            return request;
        }
        return new ChatRagRequestDto(
                request.chat(),
                request.ragQuery(),
                request.ragTopK(),
                request.objectType(),
                request.objectId(),
                profileId,
                null,
                null,
                request.topK(),
                request.minScore(),
                request.debug(),
                request.retrievalStrategy(),
                request.retrievalOptions(),
                deploymentId);
    }

    private boolean hasExplicitEmbeddingSelection(ChatRagRequestDto request) {
        return normalize(request.embeddingDeploymentId()) != null
                || normalize(request.embeddingProfileId()) != null
                || normalize(request.embeddingProvider()) != null
                || normalize(request.embeddingModel()) != null;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RetrievalResolution(
            Strategy strategy,
            String embeddingDeploymentId,
            String embeddingProfileId) {
    }

    private List<LegResult> ideaBlockLegs(
            ChatRagRequestDto request,
            String resolvedQuery,
            MetadataFilter baseFilter,
            RetrievalPlan plan,
            Integer requestedTopK) {
        return List.of(
                searchLeg("ideaBlock.actualChunkingStrategy", request, resolvedQuery, plan.ideaBlockTopK(),
                        withEquals(baseFilter, KEY_ACTUAL_CHUNKING_STRATEGY, VALUE_BLOCKIFY),
                        plan.minScore(), requestedTopK),
                searchLeg("ideaBlock.chunkType", request, resolvedQuery, plan.ideaBlockTopK(),
                        withEquals(baseFilter, ChunkMetadata.KEY_CHUNK_TYPE, VALUE_IDEA_BLOCK),
                        plan.minScore(), requestedTopK));
    }

    private LegResult searchLeg(
            String name,
            ChatRagRequestDto request,
            String resolvedQuery,
            int topK,
            MetadataFilter filter,
            double minScore,
            Integer requestedTopK) {
        List<RagSearchResult> results = search(request, resolvedQuery, topK, filter, minScore, requestedTopK, minScore);
        return new LegResult(name, topK, results == null ? List.of() : List.copyOf(results));
    }

    private List<RagSearchResult> search(
            ChatRagRequestDto request,
            String resolvedQuery,
            int topK,
            MetadataFilter filter,
            double minScore,
            Integer requestedTopK,
            Double requestedMinScore) {
        return ragPipelineService.search(new RagSearchRequest(
                resolvedQuery,
                topK,
                filter,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                minScore,
                requestedTopK,
                requestedMinScore,
                queryExpansionEnabled(request),
                request.embeddingDeploymentId()));
    }

    private boolean queryExpansionEnabled(ChatRagRequestDto request) {
        ChatRagRetrievalOptionsDto options = request == null ? null : request.retrievalOptions();
        return options == null
                || options.queryExpansionEnabled() == null
                || options.queryExpansionEnabled();
    }

    private Double requestedMinScore(ChatRagRequestDto request) {
        if (request.minScore() != null) {
            return request.minScore();
        }
        return request.retrievalOptions() == null ? null : request.retrievalOptions().minScore();
    }

    private MetadataFilter baseFilter(String objectType, String objectId) {
        if (objectType == null && objectId == null) {
            return MetadataFilter.empty();
        }
        return MetadataFilter.objectScope(objectType, objectId);
    }

    private MetadataFilter withEquals(MetadataFilter filter, String key, Object value) {
        Map<String, Object> equals = new LinkedHashMap<>(filter.equalsCriteria());
        equals.put(key, value);
        return MetadataFilter.of(equals, filter.inCriteria(), filter.rangeCriteria());
    }

    private List<RagSearchResult> merge(List<LegResult> legs, int finalTopK, boolean dedupe, double distilledScoreBoost) {
        List<RagSearchResult> candidates = legs.stream()
                .flatMap(leg -> leg.results().stream())
                .sorted(Comparator.comparingDouble((RagSearchResult result) -> rankingScore(result, distilledScoreBoost))
                        .reversed())
                .toList();
        if (!dedupe) {
            return limit(candidates, finalTopK);
        }
        Map<String, RagSearchResult> merged = new LinkedHashMap<>();
        for (RagSearchResult candidate : candidates) {
            merged.putIfAbsent(dedupeKey(candidate), candidate);
        }
        return limit(new ArrayList<>(merged.values()), finalTopK);
    }

    private double rankingScore(RagSearchResult result, double distilledScoreBoost) {
        if (distilledScoreBoost <= 0.0d || result.metadata() == null) {
            return result.score();
        }
        return hasTruthyMetadata(result.metadata(), "ideaBlockDistilled", "distilled")
                ? result.score() + distilledScoreBoost
                : result.score();
    }

    private boolean hasTruthyMetadata(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(Objects.toString(value, ""))) {
                return true;
            }
        }
        return false;
    }

    private List<RagSearchResult> limit(List<RagSearchResult> results, int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream().limit(Math.max(0, topK)).toList();
    }

    private String dedupeKey(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String key = firstText(metadata, KEY_DOCUMENT_CHUNK_ID, KEY_VECTOR_DOCUMENT_CHUNK_ID, "chunkId",
                RagContextBuilder.KEY_CHUNK_ID);
        if (key != null) {
            return key;
        }
        if (result.documentId() != null && !result.documentId().isBlank()) {
            return result.documentId();
        }
        return "content:" + Integer.toHexString(Objects.toString(result.content(), "").hashCode());
    }

    private static String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !Objects.toString(value, "").isBlank()) {
                return Objects.toString(value).trim();
            }
        }
        return null;
    }

    private record RetrievalPlan(
            String requestedStrategy,
            int structureTopK,
            int ideaBlockTopK,
            int finalTopK,
            double minScore,
            boolean dedupe,
            double distilledScoreBoost) {

        static RetrievalPlan from(
                ChatRagRequestDto request,
                AiWebRagProperties.RetrievalProperties properties,
                int defaultTopK,
                double defaultMinScore) {
            ChatRagRetrievalOptionsDto options = request.retrievalOptions();
            String strategy = firstNonBlank(request.retrievalStrategy(), properties.getDefaultStrategy(), STRATEGY_DEFAULT);
            int finalTopK = positive(options == null ? null : options.finalTopK(), properties.getFinalTopK(), defaultTopK);
            return new RetrievalPlan(
                    strategy,
                    positive(options == null ? null : options.structureTopK(), properties.getStructureTopK(), defaultTopK),
                    positive(options == null ? null : options.ideaBlockTopK(), properties.getIdeaBlockTopK(), defaultTopK),
                    finalTopK,
                    request.minScore() != null ? request.minScore()
                            : options != null && options.minScore() != null ? options.minScore() : defaultMinScore,
                    options != null && options.dedupe() != null ? options.dedupe() : properties.isDedupe(),
                    options != null && options.distilledScoreBoost() != null
                            ? options.distilledScoreBoost()
                            : properties.getDistilledScoreBoost());
        }

        private static int positive(Integer requested, int configured, int fallback) {
            if (requested != null && requested > 0) {
                return requested;
            }
            if (configured > 0) {
                return configured;
            }
            return Math.max(1, fallback);
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return STRATEGY_DEFAULT;
        }
    }

    public record RetrievalResult(List<RagSearchResult> results, RetrievalDebug debug) {
    }

    public record LegResult(String strategy, int topK, List<RagSearchResult> results) {

        Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("strategy", strategy);
            metadata.put("topK", topK);
            metadata.put("candidateCount", results.size());
            metadata.put("returnedCount", results.size());
            return metadata;
        }
    }

    public record RetrievalDebug(
            String requestedStrategy,
            String resolvedStrategy,
            List<LegResult> legs,
            List<RagSearchResult> finalResults,
            boolean includeDebugChunks) {

        static RetrievalDebug disabled() {
            return new RetrievalDebug(null, null, List.of(), List.of(), false);
        }

        public boolean enabled() {
            return requestedStrategy != null;
        }

        public Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("requestedStrategy", requestedStrategy);
            metadata.put("resolvedStrategy", resolvedStrategy);
            metadata.put("legs", legs.stream().map(LegResult::toMetadata).toList());
            metadata.put("finalCount", finalResults.size());
            if (includeDebugChunks) {
                metadata.put("chunks", finalResults.stream().map(this::chunkMetadata).toList());
            }
            return metadata;
        }

        private Map<String, Object> chunkMetadata(RagSearchResult result) {
            Map<String, Object> source = result.metadata() == null ? Map.of() : result.metadata();
            Map<String, Object> metadata = new LinkedHashMap<>();
            put(metadata, "chunkId", firstText(source, RagContextBuilder.KEY_CHUNK_ID, "chunkId"));
            put(metadata, "documentChunkId", firstText(source, KEY_DOCUMENT_CHUNK_ID, KEY_VECTOR_DOCUMENT_CHUNK_ID));
            metadata.put("score", result.score());
            put(metadata, "strategy", firstText(source, ChunkMetadata.KEY_STRATEGY, "strategy"));
            put(metadata, "chunkType", firstText(source, ChunkMetadata.KEY_CHUNK_TYPE, "chunkType"));
            put(metadata, "actualChunkingStrategy", firstText(source, KEY_ACTUAL_CHUNKING_STRATEGY));
            put(metadata, "markdownDocumentId", firstText(source, "markdownDocumentId"));
            put(metadata, "markdownRevisionId", firstText(source, "markdownRevisionId"));
            put(metadata, "sectionTitle", firstText(source, "sectionTitle", "section", "headingPath"));
            put(metadata, "ideaBlockDistilled", source.get("ideaBlockDistilled"));
            put(metadata, "distilled", source.get("distilled"));
            put(metadata, "ideaBlockDistillationFingerprint", source.get("ideaBlockDistillationFingerprint"));
            put(metadata, "ideaBlockDistillationKey", source.get("ideaBlockDistillationKey"));
            put(metadata, "ideaBlockDistilledFromChunkIds", source.get("ideaBlockDistilledFromChunkIds"));
            return metadata;
        }

        private void put(Map<String, Object> target, String key, Object value) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private enum Strategy {
        DEFAULT(STRATEGY_DEFAULT),
        STRUCTURE(STRATEGY_STRUCTURE),
        IDEA_BLOCK(STRATEGY_IDEA_BLOCK),
        HYBRID(STRATEGY_HYBRID),
        AUTO(STRATEGY_AUTO);

        private final String value;

        Strategy(String value) {
            this.value = value;
        }

        static Strategy from(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT;
            }
            String normalized = value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "structure" -> STRUCTURE;
                case "ideablock" -> IDEA_BLOCK;
                case "hybrid" -> HYBRID;
                case "auto" -> AUTO;
                case "default" -> DEFAULT;
                default -> throw new IllegalArgumentException("Unsupported retrievalStrategy: " + value);
            };
        }
    }
}

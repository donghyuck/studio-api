package studio.one.platform.chunking.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.NormalizedBlock;

public class PresidioPiiMaskingClient implements BlockifyPiiMaskingPort {

    private static final TypeReference<List<AnalyzerResult>> ANALYZER_RESULTS = new TypeReference<>() {
    };

    private final URI analyzerUri;
    @SuppressWarnings("unused")
    private final URI anonymizerUri;
    private final String language;
    private final double minScore;
    private final Set<String> allowedEntityTypes;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PresidioPiiMaskingClient(
            ChunkingProperties.BlockifyPiiMaskingProperties properties,
            ObjectMapper objectMapper) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build(), objectMapper);
    }

    PresidioPiiMaskingClient(
            ChunkingProperties.BlockifyPiiMaskingProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        Objects.requireNonNull(properties, "properties");
        this.analyzerUri = URI.create(stripTrailingSlash(properties.getAnalyzerUrl()) + "/analyze");
        this.anonymizerUri = URI.create(stripTrailingSlash(properties.getAnonymizerUrl()) + "/anonymize");
        this.language = properties.getLanguage();
        this.minScore = properties.getMinScore();
        this.allowedEntityTypes = properties.getEntityTypes();
        this.timeout = properties.getTimeout();
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public MaskedRequest mask(BlockifyGenerationRequest request) {
        Map<String, String> replacements = new LinkedHashMap<>();
        Set<String> entityTypes = new LinkedHashSet<>();
        List<NormalizedBlock> blocks = request.blocks() == null ? List.of() : request.blocks().stream()
                .map(block -> maskBlock(block, replacements, entityTypes))
                .toList();
        return new MaskedRequest(withBlocks(request, blocks), Map.copyOf(replacements),
                replacements.size(), Set.copyOf(entityTypes));
    }

    @Override
    public String deanonymize(String text, MaskedRequest maskedRequest) {
        if (text == null || text.isBlank() || maskedRequest == null || maskedRequest.replacements().isEmpty()) {
            return text;
        }
        String restored = text;
        for (Map.Entry<String, String> entry : maskedRequest.replacements().entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }

    private NormalizedBlock maskBlock(
            NormalizedBlock block,
            Map<String, String> replacements,
            Set<String> entityTypes) {
        if (block == null || block.text().isBlank()) {
            return block;
        }
        List<AnalyzerResult> results = analyze(block.text()).stream()
                .filter(result -> result.score() >= minScore)
                .filter(result -> allowedEntityTypes.isEmpty() || allowedEntityTypes.contains(result.entity_type()))
                .sorted(Comparator.comparingInt(AnalyzerResult::start))
                .toList();
        if (results.isEmpty()) {
            return block;
        }
        List<AnalyzerResult> sanitized = nonOverlapping(results, block.text().length());
        String masked = block.text();
        for (int index = sanitized.size() - 1; index >= 0; index--) {
            AnalyzerResult result = sanitized.get(index);
            String original = masked.substring(result.start(), result.end());
            String token = "<PII_" + replacements.size() + "_" + result.entity_type() + ">";
            replacements.put(token, original);
            entityTypes.add(result.entity_type());
            masked = masked.substring(0, result.start()) + token + masked.substring(result.end());
        }
        return new NormalizedBlock(
                block.id(),
                block.type(),
                masked,
                block.sourceRef(),
                block.page(),
                block.slide(),
                block.order(),
                block.parentBlockId(),
                block.headingPath(),
                block.blockIds(),
                block.confidence(),
                block.metadata());
    }

    private List<AnalyzerResult> analyze(String text) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "text", text,
                    "language", language));
            HttpRequest request = HttpRequest.newBuilder(analyzerUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Presidio analyzer failed with status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), ANALYZER_RESULTS);
        } catch (Exception ex) {
            throw new IllegalStateException("Presidio analyzer request failed", ex);
        }
    }

    private List<AnalyzerResult> nonOverlapping(List<AnalyzerResult> values, int length) {
        List<AnalyzerResult> sanitized = new ArrayList<>();
        int previousEnd = -1;
        for (AnalyzerResult value : values) {
            if (value.start() < 0 || value.end() > length || value.start() >= value.end()) {
                continue;
            }
            if (value.start() < previousEnd) {
                continue;
            }
            sanitized.add(value);
            previousEnd = value.end();
        }
        return sanitized;
    }

    private BlockifyGenerationRequest withBlocks(BlockifyGenerationRequest request, List<NormalizedBlock> blocks) {
        return new BlockifyGenerationRequest(
                request.sourceDocumentId(),
                request.sectionId(),
                request.headingPath(),
                blocks,
                request.promptVersion(),
                request.llmProvider(),
                request.llmModel(),
                request.generatorModel(),
                request.temperature(),
                request.topP(),
                request.piiMaskingEnabled(),
                request.maxBlocksPerSection(),
                request.documentType(),
                request.blockifyProfile(),
                request.ideaBlockSchemaVersion());
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record AnalyzerResult(int start, int end, double score, String entity_type) {
    }
}

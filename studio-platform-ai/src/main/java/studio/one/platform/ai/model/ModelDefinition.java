package studio.one.platform.ai.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record ModelDefinition(
        String catalogId,
        String displayName,
        String description,
        String providerFamily,
        String apiModel,
        Set<ModelWorkload> workloads,
        Set<Modality> inputModalities,
        Set<Modality> outputModalities,
        Set<String> capabilities,
        ModelDimensionPolicy dimensionPolicy,
        ModelLifecycle lifecycle,
        Set<String> aliases,
        Integer contextWindow,
        Integer maxOutputTokens,
        Set<String> reasoningModes,
        boolean toolCalling,
        boolean structuredOutput,
        ModelDistribution distribution,
        String license,
        List<String> artifactRefs,
        List<ModelRuntimeMapping> runtimeMappings,
        Set<String> protocolRequirements,
        ModelCatalogTier catalogTier,
        String catalogSource,
        String sourceUrl,
        String sourceRevision,
        String catalogVersion,
        String releasedAt,
        String verifiedAt) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._@/-]*");

    public ModelDefinition {
        catalogId = canonicalId(catalogId, "catalogId");
        providerFamily = canonicalId(providerFamily, "providerFamily");
        apiModel = required(apiModel, "apiModel");
        displayName = optional(displayName);
        description = optional(description);
        workloads = immutable(workloads);
        inputModalities = immutable(inputModalities);
        outputModalities = immutable(outputModalities);
        capabilities = immutable(capabilities);
        dimensionPolicy = dimensionPolicy == null ? ModelDimensionPolicy.none() : dimensionPolicy;
        lifecycle = required(lifecycle, "lifecycle");
        aliases = aliases == null ? Set.of() : aliases.stream()
                .map(alias -> canonicalId(alias, "alias"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        reasoningModes = immutable(reasoningModes);
        distribution = required(distribution, "distribution");
        artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
        runtimeMappings = runtimeMappings == null ? List.of() : List.copyOf(runtimeMappings);
        protocolRequirements = immutable(protocolRequirements);
        catalogTier = required(catalogTier, "catalogTier");
        catalogSource = required(catalogSource, "catalogSource");
        sourceUrl = required(sourceUrl, "sourceUrl");
        catalogVersion = required(catalogVersion, "catalogVersion");
        verifiedAt = required(verifiedAt, "verifiedAt");
        license = optional(license);
        sourceRevision = optional(sourceRevision);
        releasedAt = optional(releasedAt);
        if (workloads.isEmpty()) {
            throw new IllegalArgumentException("workloads must not be empty");
        }
        if (inputModalities.isEmpty()) {
            throw new IllegalArgumentException("inputModalities must not be empty");
        }
        if (contextWindow != null && contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }

    public boolean supports(ModelWorkload workload) {
        return workloads.contains(workload);
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String canonicalId(String value, String field) {
        String canonical = required(value, field).toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(canonical).matches()) {
            throw new IllegalArgumentException(field + " has invalid characters: " + value);
        }
        return canonical;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

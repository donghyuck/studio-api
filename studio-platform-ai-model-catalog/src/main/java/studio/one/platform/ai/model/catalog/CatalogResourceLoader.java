package studio.one.platform.ai.model.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import studio.one.platform.ai.model.Modality;
import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDimensionPolicy;
import studio.one.platform.ai.model.ModelDistribution;
import studio.one.platform.ai.model.ModelLifecycle;
import studio.one.platform.ai.model.ModelRuntimeMapping;
import studio.one.platform.ai.model.ModelWorkload;

public final class CatalogResourceLoader {

    private final ObjectMapper objectMapper;

    public CatalogResourceLoader() {
        // This mapper only reads the versioned, bundled catalog resource. It is not
        // used for HTTP, Redis, database, or other application payload contracts.
        this(JsonMapper.builder().build());
    }

    CatalogResourceLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public DefaultModelCatalog load(InputStream input) {
        if (input == null) {
            throw new CatalogValidationException("catalog resource was not found");
        }
        try (input) {
            JsonNode root = objectMapper.readTree(input);
            String catalogVersion = requiredText(root, "catalogVersion");
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                throw new CatalogValidationException("models must be an array");
            }
            List<ModelDefinition> definitions = new ArrayList<>();
            for (JsonNode model : models) {
                definitions.add(toDefinition(model, catalogVersion));
            }
            return new DefaultModelCatalog(definitions);
        } catch (IOException exception) {
            throw new CatalogValidationException("failed to read catalog resource: " + exception.getMessage());
        }
    }

    private ModelDefinition toDefinition(JsonNode node, String catalogVersion) {
        return new ModelDefinition(
                requiredText(node, "catalogId"),
                text(node, "displayName"),
                text(node, "description"),
                requiredText(node, "providerFamily"),
                requiredText(node, "apiModel"),
                enums(node, "workloads", ModelWorkload.class),
                enums(node, "inputModalities", Modality.class),
                enums(node, "outputModalities", Modality.class),
                strings(node, "capabilities"),
                dimensions(node.path("dimensionPolicy")),
                enumValue(node, "lifecycle", ModelLifecycle.class),
                strings(node, "aliases"),
                integer(node, "contextWindow"),
                integer(node, "maxOutputTokens"),
                strings(node, "reasoningModes"),
                node.path("toolCalling").asBoolean(false),
                node.path("structuredOutput").asBoolean(false),
                enumValue(node, "distribution", ModelDistribution.class),
                text(node, "license"),
                List.copyOf(strings(node, "artifactRefs")),
                runtimeMappings(node.path("runtimeMappings")),
                strings(node, "protocolRequirements"),
                enumValue(node, "catalogTier", ModelCatalogTier.class),
                requiredText(node, "catalogSource"),
                requiredText(node, "sourceUrl"),
                text(node, "sourceRevision"),
                catalogVersion,
                text(node, "releasedAt"),
                requiredText(node, "verifiedAt"));
    }

    private ModelDimensionPolicy dimensions(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return ModelDimensionPolicy.none();
        }
        Set<Integer> supported = new LinkedHashSet<>();
        node.path("supported").forEach(item -> supported.add(item.asInt()));
        Integer defaultDimension = node.hasNonNull("defaultDimension")
                ? node.path("defaultDimension").asInt()
                : null;
        return new ModelDimensionPolicy(supported, defaultDimension);
    }

    private List<ModelRuntimeMapping> runtimeMappings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<ModelRuntimeMapping> mappings = new ArrayList<>();
        node.forEach(item -> mappings.add(new ModelRuntimeMapping(
                requiredText(item, "runtime"), requiredText(item, "model"), requiredText(item, "protocol"))));
        return List.copyOf(mappings);
    }

    private <E extends Enum<E>> Set<E> enums(JsonNode node, String field, Class<E> type) {
        Set<E> values = new LinkedHashSet<>();
        node.path(field).forEach(item -> values.add(Enum.valueOf(type, item.asText())));
        return Set.copyOf(values);
    }

    private <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        return Enum.valueOf(type, requiredText(node, field));
    }

    private Set<String> strings(JsonNode node, String field) {
        Set<String> values = new LinkedHashSet<>();
        node.path(field).forEach(item -> values.add(item.asText()));
        return Set.copyOf(values);
    }

    private Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asInt() : null;
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new CatalogValidationException(field + " must not be blank");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }
}

package studio.one.platform.ai.model.catalog;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.model.ModelCatalogTier;
import studio.one.platform.ai.model.ModelDefinition;
import studio.one.platform.ai.model.ModelDistribution;
import studio.one.platform.ai.model.ModelWorkload;

public final class ModelCatalogValidator {

    public void validate(List<ModelDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new CatalogValidationException("catalog must contain at least one model");
        }
        Map<String, String> owners = new HashMap<>();
        for (ModelDefinition definition : definitions) {
            register(owners, definition.catalogId(), definition.catalogId());
            definition.aliases().forEach(alias -> register(owners, alias, definition.catalogId()));
            validateSource(definition);
            validateEmbedding(definition);
            if (definition.distribution() == ModelDistribution.OPEN_WEIGHT
                    && definition.catalogTier() == ModelCatalogTier.ADAPTER_READY
                    && definition.runtimeMappings().isEmpty()) {
                throw new CatalogValidationException(
                        definition.catalogId() + " is ADAPTER_READY but has no runtime mapping");
            }
        }
    }

    private void register(Map<String, String> owners, String key, String owner) {
        String previous = owners.putIfAbsent(key, owner);
        if (previous != null) {
            throw new CatalogValidationException(
                    "duplicate catalog id or alias '" + key + "' owned by " + previous + " and " + owner);
        }
    }

    private void validateSource(ModelDefinition definition) {
        try {
            URI source = URI.create(definition.sourceUrl());
            if (!source.isAbsolute() || !("https".equalsIgnoreCase(source.getScheme())
                    || "http".equalsIgnoreCase(source.getScheme()))) {
                throw new CatalogValidationException(definition.catalogId() + " has an invalid sourceUrl");
            }
            LocalDate.parse(definition.verifiedAt());
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new CatalogValidationException(
                    definition.catalogId() + " has invalid source metadata: " + exception.getMessage());
        }
    }

    private void validateEmbedding(ModelDefinition definition) {
        if (definition.workloads().contains(ModelWorkload.EMBEDDING)
                && definition.dimensionPolicy().supported().isEmpty()) {
            throw new CatalogValidationException(
                    definition.catalogId() + " is an embedding model without supported dimensions");
        }
    }
}

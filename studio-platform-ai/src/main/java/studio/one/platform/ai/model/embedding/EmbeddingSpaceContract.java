package studio.one.platform.ai.model.embedding;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record EmbeddingSpaceContract(
        String contractVersion,
        String providerFamily,
        String apiModel,
        int dimension,
        String normalizationPolicy,
        String indexTaskType,
        String queryTaskType,
        String inputTransformId,
        String inputTransformVersion,
        Map<String, String> semanticOptions) {

    public EmbeddingSpaceContract {
        contractVersion = identifier(contractVersion, "contractVersion");
        providerFamily = identifier(providerFamily, "providerFamily");
        apiModel = required(apiModel, "apiModel");
        normalizationPolicy = identifier(normalizationPolicy, "normalizationPolicy");
        indexTaskType = nullableIdentifier(indexTaskType);
        queryTaskType = nullableIdentifier(queryTaskType);
        inputTransformId = nullableIdentifier(inputTransformId);
        inputTransformVersion = nullableIdentifier(inputTransformVersion);
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        if (semanticOptions != null) {
            semanticOptions.forEach((key, value) -> sorted.put(
                    required(key, "semanticOptions key"),
                    value == null ? null : value.trim()));
        }
        semanticOptions = Collections.unmodifiableMap(sorted);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String identifier(String value, String field) {
        return required(value, field).toLowerCase(Locale.ROOT);
    }

    private static String nullableIdentifier(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}

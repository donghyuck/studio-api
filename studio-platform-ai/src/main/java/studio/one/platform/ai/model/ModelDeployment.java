package studio.one.platform.ai.model;

import java.util.Locale;
import java.util.Map;

import studio.one.platform.ai.model.embedding.EmbeddingSpaceContract;

public record ModelDeployment(
        String deploymentId,
        String providerRef,
        ModelDefinition definition,
        ModelWorkload workload,
        Integer dimension,
        boolean enabled,
        EmbeddingSpaceContract embeddingContract) {

    public ModelDeployment(
            String deploymentId,
            String providerRef,
            ModelDefinition definition,
            ModelWorkload workload,
            Integer dimension,
            boolean enabled) {
        this(deploymentId, providerRef, definition, workload, dimension, enabled,
                defaultEmbeddingContract(definition, workload, dimension));
    }

    public ModelDeployment {
        deploymentId = canonical(deploymentId, "deploymentId");
        providerRef = canonical(providerRef, "providerRef");
        if (definition == null || workload == null) {
            throw new IllegalArgumentException("definition and workload must not be null");
        }
        if (!definition.supports(workload)) {
            throw new IllegalArgumentException("model does not support workload " + workload);
        }
        if (dimension != null && !definition.dimensionPolicy().supported().isEmpty()
                && !definition.dimensionPolicy().supported().contains(dimension)) {
            throw new IllegalArgumentException("unsupported embedding dimension " + dimension);
        }
        if (workload == ModelWorkload.EMBEDDING) {
            if (dimension == null || embeddingContract == null) {
                throw new IllegalArgumentException("embedding deployment requires dimension and embeddingContract");
            }
            if (embeddingContract.dimension() != dimension
                    || !embeddingContract.providerFamily().equals(definition.providerFamily())
                    || !embeddingContract.apiModel().equals(definition.apiModel())) {
                throw new IllegalArgumentException("embeddingContract must match deployment model and dimension");
            }
        } else if (embeddingContract != null) {
            throw new IllegalArgumentException("embeddingContract is only valid for embedding deployments");
        }
    }

    private static String canonical(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static EmbeddingSpaceContract defaultEmbeddingContract(
            ModelDefinition definition, ModelWorkload workload, Integer dimension) {
        if (definition == null || workload != ModelWorkload.EMBEDDING || dimension == null) {
            return null;
        }
        return new EmbeddingSpaceContract(
                "v1", definition.providerFamily(), definition.apiModel(), dimension,
                "provider-default", "provider-default", "provider-default", "text", "1", Map.of());
    }
}

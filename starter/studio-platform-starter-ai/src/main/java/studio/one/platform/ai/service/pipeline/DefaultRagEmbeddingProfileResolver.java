package studio.one.platform.ai.service.pipeline;

import java.util.Map;
import java.util.Locale;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.rag.RagEmbeddingProfile;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceId;

public class DefaultRagEmbeddingProfileResolver implements RagEmbeddingProfileResolver {

    private final EmbeddingPort defaultEmbeddingPort;
    private final ModelDeploymentRegistry providerRegistry;
    private final String defaultProfileId;
    private final Map<String, RagEmbeddingProfile> profiles;

    public DefaultRagEmbeddingProfileResolver(
            EmbeddingPort defaultEmbeddingPort,
            ModelDeploymentRegistry providerRegistry,
            String defaultProfileId,
            Map<String, RagEmbeddingProfile> profiles) {
        this.defaultEmbeddingPort = java.util.Objects.requireNonNull(defaultEmbeddingPort, "defaultEmbeddingPort");
        this.providerRegistry = java.util.Objects.requireNonNull(providerRegistry, "providerRegistry");
        this.defaultProfileId = normalize(defaultProfileId);
        this.profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    @Override
    public ResolvedRagEmbedding resolve(RagEmbeddingSelection selection) {
        RagEmbeddingSelection requested = selection == null
                ? new RagEmbeddingSelection(null, null, null, null, EmbeddingInputType.TEXT, null)
                : selection;
        if (requested.deploymentId() != null) {
            return resolveDeployment(requested);
        }
        String profileId = requested.profileId();
        if (profileId == null && requested.isLegacyDefault()) {
            if (defaultProfileId != null) {
                profileId = defaultProfileId;
            } else {
                var defaultDeployment = providerRegistry.defaultDeployment(ModelWorkload.EMBEDDING);
                if (defaultDeployment.isPresent()) {
                    return resolveDeployment(new RagEmbeddingSelection(
                            null,
                            null,
                            null,
                            requested.dimension(),
                            requested.inputType(),
                            defaultDeployment.get().deploymentId()));
                }
            }
        }
        if (profileId != null && (requested.provider() != null || requested.model() != null)) {
            throw new IllegalArgumentException(
                    "embeddingProvider/embeddingModel must not be supplied with embeddingProfileId");
        }
        RagEmbeddingProfile profile = profile(profileId);
        if (profileId != null && profile == null) {
            throw new IllegalArgumentException("Unknown RAG embedding profile: " + profileId);
        }
        String profileDeploymentId = metadataText(profile, "deploymentId");
        if (profileDeploymentId != null) {
            return resolveDeployment(new RagEmbeddingSelection(
                    null, null, null, requested.dimension(), requested.inputType(), profileDeploymentId));
        }
        if (profile != null && requested.dimension() != null && profile.dimension() != null
                && !requested.dimension().equals(profile.dimension())) {
            throw new IllegalArgumentException("embeddingDimension does not match RAG embedding profile '"
                    + profile.profileId() + "': expected " + profile.dimension()
                    + ", requested " + requested.dimension());
        }
        if (profile != null && !profile.supports(requested.inputType())) {
            throw new IllegalArgumentException("RAG embedding profile '" + profile.profileId()
                    + "' does not support input type " + requested.inputType());
        }

        String adapterProvider = firstNonBlank(requested.provider(), profile == null ? null : profile.provider());
        String provider = profile == null
                ? adapterProvider
                : firstNonBlank(metadataText(profile, "providerId"), adapterProvider);
        String model = firstNonBlank(requested.model(), profile == null ? null : profile.model());
        Integer dimension = requested.dimension() != null
                ? requested.dimension()
                : profile == null ? null : profile.dimension();
        EmbeddingPort port = adapterProvider == null
                ? defaultEmbeddingPort
                : providerRegistry.embeddingPort(adapterProvider);
        String modelId = profile == null ? null : profile.profileId();
        String embeddingSpaceId = profile == null
                ? null
                : firstNonBlank(metadataText(profile, "embeddingSpaceId"), modelId);
        return new ResolvedRagEmbedding(
                port,
                profile == null ? profileId : profile.profileId(),
                provider,
                model,
                dimension,
                requested.inputType(),
                modelId,
                embeddingSpaceId);
    }

    private ResolvedRagEmbedding resolveDeployment(RagEmbeddingSelection requested) {
        if (requested.profileId() != null || requested.provider() != null || requested.model() != null) {
            throw new IllegalArgumentException(
                    "embeddingDeploymentId must not be supplied with legacy embedding selection fields");
        }
        ModelDeployment deployment = findDeployment(requested.deploymentId());
        if (deployment.workload() != ModelWorkload.EMBEDDING) {
            throw new IllegalArgumentException("Deployment is not an embedding deployment: " + deployment.deploymentId());
        }
        Integer dimension = requested.dimension() == null ? deployment.dimension() : requested.dimension();
        if (dimension == null) {
            throw new IllegalArgumentException("Embedding deployment has no dimension: " + deployment.deploymentId());
        }
        if (deployment.dimension() != null && !deployment.dimension().equals(dimension)) {
            throw new IllegalArgumentException("embeddingDimension does not match deployment "
                    + deployment.deploymentId());
        }
        var contract = deployment.embeddingContract();
        String spaceId = EmbeddingSpaceId.from(contract);
        return new ResolvedRagEmbedding(
                providerRegistry.embeddingPort(deployment.deploymentId()),
                deployment.deploymentId(),
                deployment.providerRef(),
                deployment.definition().apiModel(),
                dimension,
                requested.inputType(),
                deployment.definition().catalogId(),
                spaceId,
                deployment.deploymentId(),
                deployment.definition().catalogId(),
                contract.contractVersion());
    }

    private ModelDeployment findDeployment(String requestedId) {
        var direct = providerRegistry.find(requestedId);
        if (direct.isPresent()) {
            return direct.get();
        }
        String normalized = normalize(requestedId);
        var matches = providerRegistry.deployments(ModelWorkload.EMBEDDING).stream()
                .filter(deployment -> deployment.definition().catalogId().equalsIgnoreCase(normalized)
                        || deployment.definition().aliases().stream()
                                .anyMatch(alias -> alias.equalsIgnoreCase(normalized)))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous embedding model identifier: " + requestedId + ". Use embeddingDeploymentId.");
        }
        throw new IllegalArgumentException("Unknown embedding deployment: " + requestedId);
    }

    private RagEmbeddingProfile profile(String profileId) {
        String key = normalize(profileId);
        return key == null ? null : profiles.get(key.toLowerCase(Locale.ROOT));
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? normalize(fallback) : primary.trim();
    }

    private static String metadataText(RagEmbeddingProfile profile, String key) {
        Object value = profile == null ? null : profile.metadata().get(key);
        return value == null ? null : normalize(value.toString());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

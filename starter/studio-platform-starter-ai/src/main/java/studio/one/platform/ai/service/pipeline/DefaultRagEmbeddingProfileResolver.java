package studio.one.platform.ai.service.pipeline;

import java.util.Locale;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.rag.RagEmbeddingProfile;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

public class DefaultRagEmbeddingProfileResolver implements RagEmbeddingProfileResolver {

    private final EmbeddingPort defaultEmbeddingPort;
    private final AiProviderRegistry providerRegistry;
    private final String defaultProfileId;
    private final Map<String, RagEmbeddingProfile> profiles;

    public DefaultRagEmbeddingProfileResolver(
            EmbeddingPort defaultEmbeddingPort,
            AiProviderRegistry providerRegistry,
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
                ? new RagEmbeddingSelection(null, null, null, null, EmbeddingInputType.TEXT)
                : selection;
        String profileId = requested.profileId();
        if (profileId == null && requested.isLegacyDefault()) {
            profileId = defaultProfileId;
        }
        if (profileId != null && (requested.provider() != null || requested.model() != null)) {
            throw new IllegalArgumentException(
                    "embeddingProvider/embeddingModel must not be supplied with embeddingProfileId");
        }
        RagEmbeddingProfile profile = profile(profileId);
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

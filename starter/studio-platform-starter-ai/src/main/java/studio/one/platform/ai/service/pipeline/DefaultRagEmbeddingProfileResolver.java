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
                ? new RagEmbeddingSelection(null, null, null, EmbeddingInputType.TEXT)
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
        if (profile != null && !profile.supports(requested.inputType())) {
            throw new IllegalArgumentException("RAG embedding profile '" + profile.profileId()
                    + "' does not support input type " + requested.inputType());
        }

        String provider = firstNonBlank(requested.provider(), profile == null ? null : profile.provider());
        String model = firstNonBlank(requested.model(), profile == null ? null : profile.model());
        Integer dimension = profile == null ? null : profile.dimension();
        EmbeddingPort port = provider == null
                ? defaultEmbeddingPort
                : providerRegistry.embeddingPort(provider);
        return new ResolvedRagEmbedding(
                port,
                profile == null ? profileId : profile.profileId(),
                provider,
                model,
                dimension,
                requested.inputType());
    }

    private RagEmbeddingProfile profile(String profileId) {
        String key = normalize(profileId);
        return key == null ? null : profiles.get(key.toLowerCase(Locale.ROOT));
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? normalize(fallback) : primary.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

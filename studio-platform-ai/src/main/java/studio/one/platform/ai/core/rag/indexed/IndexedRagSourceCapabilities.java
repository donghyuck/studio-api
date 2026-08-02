package studio.one.platform.ai.core.rag.indexed;

import java.util.List;

public record IndexedRagSourceCapabilities(
        boolean enabled,
        int maxSelectedSources,
        List<String> supportedSchemes,
        int maxUrlLength,
        List<String> collectionModes,
        boolean siteCrawlEnabled,
        int defaultMaxDepth,
        int maximumDepth,
        int defaultMaxPages,
        int maximumPages,
        int defaultMaxConcurrency,
        int maximumConcurrency,
        List<String> discoveryModes) {

    public IndexedRagSourceCapabilities {
        supportedSchemes = supportedSchemes == null ? List.of() : List.copyOf(supportedSchemes);
        collectionModes = collectionModes == null ? List.of() : List.copyOf(collectionModes);
        discoveryModes = discoveryModes == null ? List.of() : List.copyOf(discoveryModes);
    }

    public static IndexedRagSourceCapabilities singlePage(int maxSelectedSources) {
        return new IndexedRagSourceCapabilities(
                true,
                maxSelectedSources,
                List.of("https"),
                2048,
                List.of("SINGLE_PAGE"),
                false,
                0,
                0,
                1,
                1,
                1,
                1,
                List.of());
    }
}

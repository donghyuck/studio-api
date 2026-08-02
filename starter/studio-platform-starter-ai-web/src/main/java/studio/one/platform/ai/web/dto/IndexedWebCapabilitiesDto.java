package studio.one.platform.ai.web.dto;

import java.util.List;

public record IndexedWebCapabilitiesDto(
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

    public IndexedWebCapabilitiesDto {
        supportedSchemes = supportedSchemes == null ? List.of() : List.copyOf(supportedSchemes);
        collectionModes = collectionModes == null ? List.of() : List.copyOf(collectionModes);
        discoveryModes = discoveryModes == null ? List.of() : List.copyOf(discoveryModes);
    }

    public IndexedWebCapabilitiesDto(
            boolean enabled,
            int maxSelectedSources,
            List<String> supportedSchemes,
            int maxUrlLength) {
        this(
                enabled,
                maxSelectedSources,
                supportedSchemes,
                maxUrlLength,
                enabled ? List.of("SINGLE_PAGE") : List.of(),
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

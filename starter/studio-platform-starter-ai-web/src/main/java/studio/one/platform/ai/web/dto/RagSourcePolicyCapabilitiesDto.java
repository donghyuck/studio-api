package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagSourcePolicyCapabilitiesDto(
        String defaultScope,
        String maximumScope,
        boolean clientSelectionEnabled,
        boolean externalProviderAvailable,
        String policyVersion,
        List<String> availableScopes) {

    public RagSourcePolicyCapabilitiesDto {
        availableScopes = availableScopes == null ? List.of() : List.copyOf(availableScopes);
    }
}

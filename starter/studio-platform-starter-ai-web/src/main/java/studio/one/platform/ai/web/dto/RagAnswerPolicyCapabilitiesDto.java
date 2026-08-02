package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagAnswerPolicyCapabilitiesDto(
        String defaultMode,
        String maximumMode,
        boolean clientSelectionEnabled,
        String policyVersion,
        List<String> availableModes) {

    public RagAnswerPolicyCapabilitiesDto {
        availableModes = availableModes == null ? List.of() : List.copyOf(availableModes);
    }
}

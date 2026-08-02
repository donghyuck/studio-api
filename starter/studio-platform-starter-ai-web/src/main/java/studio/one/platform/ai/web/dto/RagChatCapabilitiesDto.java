package studio.one.platform.ai.web.dto;

public record RagChatCapabilitiesDto(
        RagAnswerPolicyCapabilitiesDto answerPolicy,
        RagSourcePolicyCapabilitiesDto sourcePolicy,
        IndexedWebCapabilitiesDto indexedWeb) {
}

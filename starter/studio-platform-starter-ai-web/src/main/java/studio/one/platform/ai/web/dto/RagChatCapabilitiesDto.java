package studio.one.platform.ai.web.dto;

public record RagChatCapabilitiesDto(
        RagAnswerPolicyCapabilitiesDto answerPolicy,
        RagSourcePolicyCapabilitiesDto sourcePolicy,
        IndexedWebCapabilitiesDto indexedWeb,
        RagQuestionSuggestionCapabilitiesDto questionSuggestions) {

    public RagChatCapabilitiesDto(
            RagAnswerPolicyCapabilitiesDto answerPolicy,
            RagSourcePolicyCapabilitiesDto sourcePolicy,
            IndexedWebCapabilitiesDto indexedWeb) {
        this(answerPolicy, sourcePolicy, indexedWeb, null);
    }
}

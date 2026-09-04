package studio.one.platform.ai.web.dto;

public record RagChatCapabilitiesDto(
        RagAnswerPolicyCapabilitiesDto answerPolicy,
        RagSourcePolicyCapabilitiesDto sourcePolicy,
        IndexedWebCapabilitiesDto indexedWeb,
        RagQuestionSuggestionCapabilitiesDto questionSuggestions,
        TeamRagCapabilitiesDto teamRag) {

    public RagChatCapabilitiesDto(
            RagAnswerPolicyCapabilitiesDto answerPolicy,
            RagSourcePolicyCapabilitiesDto sourcePolicy,
            IndexedWebCapabilitiesDto indexedWeb) {
        this(answerPolicy, sourcePolicy, indexedWeb, null, null);
    }

    public RagChatCapabilitiesDto(
            RagAnswerPolicyCapabilitiesDto answerPolicy,
            RagSourcePolicyCapabilitiesDto sourcePolicy,
            IndexedWebCapabilitiesDto indexedWeb,
            RagQuestionSuggestionCapabilitiesDto questionSuggestions) {
        this(answerPolicy, sourcePolicy, indexedWeb, questionSuggestions, null);
    }
}

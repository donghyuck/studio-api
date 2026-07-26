package studio.one.platform.ai.web.controller;

/**
 * Produces the canonical RAG answer shared by synchronous and SSE responses.
 */
public final class RagAnswerFinalizer {

    public static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "제공된 문서 근거만으로는 답변을 확정할 수 없습니다.";

    private final RagCitationValidator validator = new RagCitationValidator();

    public FinalizedAnswer finalizeAnswer(String draft, PackedEvidenceSet evidenceSet) {
        RagCitationValidator.Validation validation = validator.validate(draft, evidenceSet);
        String canonical = validation.valid() ? draft : INSUFFICIENT_EVIDENCE_MESSAGE;
        return new FinalizedAnswer(canonical, validation);
    }

    public record FinalizedAnswer(
            String canonicalContent,
            RagCitationValidator.Validation validation) {
    }
}

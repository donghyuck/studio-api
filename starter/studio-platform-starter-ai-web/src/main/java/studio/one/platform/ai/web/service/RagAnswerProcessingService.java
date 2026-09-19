package studio.one.platform.ai.web.service;

import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.one.platform.ai.core.chat.ChatMessage;
import studio.one.platform.ai.core.chat.ChatMessageRole;
import studio.one.platform.ai.core.chat.ChatResponse;
import studio.one.platform.ai.web.controller.PackedEvidenceSet;
import studio.one.platform.ai.web.controller.RagAnswerFinalizer;
import studio.one.platform.ai.web.controller.RagAnswerOutcome;
import studio.one.platform.ai.web.controller.RagQueryIntentClassifier;
import studio.one.platform.ai.web.controller.ResolvedRagAnswerPolicy;

/** HTTP-independent answer post-processing shared by generated and cached RAG responses. */
public final class RagAnswerProcessingService {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerProcessingService.class);
    private final RagAnswerFinalizer ragAnswerFinalizer;

    public RagAnswerProcessingService(RagAnswerFinalizer finalizer) {
        this.ragAnswerFinalizer = Objects.requireNonNull(finalizer, "finalizer");
    }

    public RagAnswerFinalizer.FinalizedAnswer process(
            ChatResponse response,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy answerPolicy,
            RagQueryIntentClassifier.Classification classification,
            boolean coverageFallback) {
        String draft = response.messages().stream()
                .filter(message -> message.role() == ChatMessageRole.ASSISTANT)
                .map(ChatMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .reduce((first, last) -> last)
                .orElse("");
        draft = ensureInterpretiveFallbackLimitation(draft, evidenceSet, coverageFallback);
        RagAnswerFinalizer.FinalizedAnswer finalized =
                ragAnswerFinalizer.finalizeAnswer(draft, evidenceSet, answerPolicy, classification);
        if (log.isDebugEnabled()) {
            RagAnswerOutcome outcome = finalized.outcome();
            log.debug(
                    "RAG answer finalization type={}, stage={}, reason={}, packedEvidenceCount={}, "
                            + "validationUnitCount={}, citedValidationUnitCount={}",
                    outcome.type(),
                    outcome.stage(),
                    outcome.reasonCode(),
                    outcome.packedEvidenceCount(),
                    outcome.validationUnitCount(),
                    outcome.citedValidationUnitCount());
        }
        return finalized;
    }

    public String ensureInterpretiveFallbackLimitation(
            String draft,
            PackedEvidenceSet evidenceSet,
            boolean coverageFallback) {
        if (!coverageFallback || draft == null || draft.isBlank()) {
            return draft;
        }
        String normalized = draft.toLowerCase(Locale.ROOT);
        if (normalized.contains("확인 한계")
                || normalized.contains("대표 구간")
                || normalized.contains("제한")
                || normalized.contains("limitation")
                || normalized.contains("representative excerpts")) {
            return draft;
        }
        int citationIndex = evidenceSet.evidence().stream()
                .mapToInt(PackedEvidenceSet.PackedEvidence::citationIndex)
                .min()
                .orElse(1);
        return draft.strip()
                + " 확인 한계: 이 해석은 문서 전체에서 선택한 대표 근거 구간을 바탕으로 하므로 "
                + "모든 세부 내용을 반영하지 못할 수 있습니다. [" + citationIndex + "]";
    }
}

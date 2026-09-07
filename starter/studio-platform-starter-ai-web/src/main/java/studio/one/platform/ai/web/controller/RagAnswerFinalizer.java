package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Produces the canonical RAG answer shared by synchronous and SSE responses.
 */
public final class RagAnswerFinalizer {

    public static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "제공된 문서 근거만으로는 답변을 확정할 수 없습니다.";
    public static final String CITATION_VALIDATION_FAILED_MESSAGE =
            "관련 근거는 찾았지만 생성 답변의 인용 검증에 실패했습니다. 아래 검색된 근거 후보를 확인해 주세요.";
    public static final String NO_MATCHING_TARGET_MESSAGE =
            "검색된 문서 근거에서는 질문 조건에 맞는 대상을 확인할 수 없습니다.";

    private final RagCitationValidator citationValidator;
    private final RagAnswerPolicyValidator policyValidator;
    private final RagCitationSyntaxNormalizer syntaxNormalizer;
    private final boolean factualListPartialAnswerEnabled;
    private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+).+");
    private static final Pattern CITATION = Pattern.compile(
            "(?<!\\d)\\[\\s*\\d{1,9}(?:\\s*,\\s*\\d{1,9})*\\s*]");

    public RagAnswerFinalizer() {
        this(new RagCitationValidator(), new RagAnswerPolicyValidator(), false);
    }

    public RagAnswerFinalizer(
            RagCitationValidator citationValidator,
            RagAnswerPolicyValidator policyValidator) {
        this(citationValidator, policyValidator, false);
    }

    public RagAnswerFinalizer(
            RagCitationValidator citationValidator,
            RagAnswerPolicyValidator policyValidator,
            boolean factualListPartialAnswerEnabled) {
        this.citationValidator = citationValidator == null ? new RagCitationValidator() : citationValidator;
        this.policyValidator = policyValidator == null ? new RagAnswerPolicyValidator() : policyValidator;
        this.syntaxNormalizer = new RagCitationSyntaxNormalizer();
        this.factualListPartialAnswerEnabled = factualListPartialAnswerEnabled;
    }

    public FinalizedAnswer finalizeAnswer(String draft, PackedEvidenceSet evidenceSet) {
        return finalizeAnswer(
                draft,
                evidenceSet,
                RagAnswerPolicyResolver.defaults().resolve(null),
                null);
    }

    public FinalizedAnswer finalizeAnswer(
            String draft,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy policy) {
        return finalizeAnswer(draft, evidenceSet, policy, null);
    }

    public FinalizedAnswer finalizeAnswer(
            String draft,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy policy,
            RagQueryIntentClassifier.Classification classification) {
        String normalizedDraft = syntaxNormalizer.normalize(draft);
        RagAnswerPolicyValidator.Validation policyValidation =
                policyValidator.validate(normalizedDraft, evidenceSet, policy, citationValidator);
        if (isNoMatchingTarget(normalizedDraft, classification)) {
            RagAnswerOutcome outcome = noMatchingTargetOutcome(evidenceSet);
            return new FinalizedAnswer(
                    NO_MATCHING_TARGET_MESSAGE,
                    policyValidation.citations(),
                    policyValidation,
                    outcome);
        }
        Optional<PartialCanonicalization> partial = partialCanonicalization(
                normalizedDraft,
                evidenceSet,
                policy,
                classification,
                policyValidation);
        if (partial.isPresent()) {
            PartialCanonicalization value = partial.get();
            RagAnswerOutcome outcome = answeredOutcome(
                    evidenceSet,
                    value.validation(),
                    true,
                    policyValidation.unitCount(),
                    value.omittedUnitCount());
            return new FinalizedAnswer(
                    value.canonicalContent(),
                    value.validation().citations(),
                    value.validation(),
                    outcome);
        }
        RagAnswerOutcome outcome = outcome(normalizedDraft, evidenceSet, policyValidation);
        String canonical = switch (outcome.type()) {
            case ANSWERED -> normalizedDraft;
            case EVIDENCE_ONLY -> CITATION_VALIDATION_FAILED_MESSAGE;
            case ABSTAINED -> INSUFFICIENT_EVIDENCE_MESSAGE;
        };
        return new FinalizedAnswer(canonical, policyValidation.citations(), policyValidation, outcome);
    }

    private boolean isNoMatchingTarget(
            String normalizedDraft,
            RagQueryIntentClassifier.Classification classification) {
        return classification != null
                && classification.intent() == RagQueryIntentClassifier.Intent.FACTUAL_LIST
                && RagAnswerPromptComposer.NO_MATCHING_TARGET_MARKER.equals(normalizedDraft);
    }

    private RagAnswerOutcome noMatchingTargetOutcome(PackedEvidenceSet evidenceSet) {
        int packedCount = evidenceSet == null ? 0 : evidenceSet.evidence().size();
        return new RagAnswerOutcome(
                RagAnswerOutcome.Type.ABSTAINED,
                RagAnswerOutcome.Stage.GENERATION,
                RagAnswerOutcome.ReasonCode.NO_MATCHING_TARGET,
                packedCount,
                packedCount,
                packedCount,
                java.util.Set.of(),
                "NOT_APPLICABLE",
                "SAFE_ABSTENTION",
                0,
                0);
    }

    private Optional<PartialCanonicalization> partialCanonicalization(
            String normalizedDraft,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy policy,
            RagQueryIntentClassifier.Classification classification,
            RagAnswerPolicyValidator.Validation originalValidation) {
        if (classification == null
                || originalValidation.status() != RagAnswerPolicyValidator.Status.MISSING_UNIT_CITATION
                || originalValidation.citations().status() != RagCitationValidator.Status.INDEX_VALID) {
            return Optional.empty();
        }
        if (classification.intent() == RagQueryIntentClassifier.Intent.INTERPRETIVE_ANALYSIS) {
            return partialInterpretiveCanonicalization(evidenceSet, policy, originalValidation);
        }
        if (!factualListPartialAnswerEnabled
                || classification.intent() != RagQueryIntentClassifier.Intent.FACTUAL_LIST) {
            return Optional.empty();
        }
        List<String> lines = normalizedDraft == null
                ? List.of()
                : normalizedDraft.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty() || lines.stream().anyMatch(line -> !LIST_ITEM.matcher(line).matches())) {
            return Optional.empty();
        }
        List<String> retained = lines.stream()
                .filter(line -> CITATION.matcher(line).find())
                .toList();
        int omitted = lines.size() - retained.size();
        if (retained.isEmpty() || omitted <= 0) {
            return Optional.empty();
        }
        String canonical = String.join("\n", retained);
        RagAnswerPolicyValidator.Validation validation =
                policyValidator.validate(canonical, evidenceSet, policy, citationValidator);
        if (!validation.valid()) {
            return Optional.empty();
        }
        return Optional.of(new PartialCanonicalization(canonical, validation, omitted));
    }

    private Optional<PartialCanonicalization> partialInterpretiveCanonicalization(
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy policy,
            RagAnswerPolicyValidator.Validation originalValidation) {
        List<String> retained = originalValidation.units().stream()
                .filter(RagAnswerPolicyValidator.ValidationUnit::cited)
                .map(RagAnswerPolicyValidator.ValidationUnit::text)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        int omitted = originalValidation.unitCount() - retained.size();
        if (retained.isEmpty() || omitted <= 0) {
            return Optional.empty();
        }
        int citationIndex = originalValidation.citations().citedIndexes().stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(1);
        String canonical = String.join(" ", retained)
                + " 확인 한계: 인용이 없는 생성 문장은 제외했으며, 남은 문서 근거 범위에서만 해석했습니다. ["
                + citationIndex + "]";
        RagAnswerPolicyValidator.Validation validation =
                policyValidator.validate(canonical, evidenceSet, policy, citationValidator);
        if (!validation.valid()) {
            return Optional.empty();
        }
        return Optional.of(new PartialCanonicalization(canonical, validation, omitted));
    }

    private RagAnswerOutcome outcome(
            String draft,
            PackedEvidenceSet evidenceSet,
            RagAnswerPolicyValidator.Validation validation) {
        int packedCount = evidenceSet == null ? 0 : evidenceSet.evidence().size();
        RagAnswerOutcome.ReasonCode reason = reasonCode(draft, validation);
        if (validation.valid()) {
            return answeredOutcome(evidenceSet, validation, false, validation.unitCount(), 0);
        }
        var usable = evidenceSet == null ? java.util.Set.<Integer>of() : evidenceSet.evidence().stream()
                .filter(item -> "SOURCE_VERIFIED".equals(item.supportStatus()))
                .filter(item -> item.sourceSpans().stream().anyMatch(span -> !span.exactText().isBlank()))
                .limit(3)
                .map(PackedEvidenceSet.PackedEvidence::citationIndex)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        RagAnswerOutcome.Type type = usable.isEmpty()
                ? RagAnswerOutcome.Type.ABSTAINED
                : RagAnswerOutcome.Type.EVIDENCE_ONLY;
        if (usable.isEmpty() && packedCount > 0) {
            reason = RagAnswerOutcome.ReasonCode.NO_USABLE_SOURCE_SPAN;
        }
        return new RagAnswerOutcome(
                type,
                RagAnswerOutcome.Stage.VALIDATION,
                reason,
                packedCount,
                packedCount,
                packedCount,
                usable,
                validation.citations().status().name(),
                validation.status().name(),
                validation.unitCount(),
                validation.citedUnitCount());
    }

    private RagAnswerOutcome answeredOutcome(
            PackedEvidenceSet evidenceSet,
            RagAnswerPolicyValidator.Validation validation,
            boolean partial,
            int originalUnitCount,
            int omittedUnitCount) {
        int packedCount = evidenceSet == null ? 0 : evidenceSet.evidence().size();
        return new RagAnswerOutcome(
                RagAnswerOutcome.Type.ANSWERED,
                RagAnswerOutcome.Stage.NONE,
                RagAnswerOutcome.ReasonCode.NONE,
                packedCount,
                packedCount,
                packedCount,
                validation.citations().citedIndexes(),
                validation.citations().status().name(),
                validation.status().name(),
                validation.unitCount(),
                validation.citedUnitCount(),
                partial,
                originalUnitCount,
                omittedUnitCount);
    }

    private RagAnswerOutcome.ReasonCode reasonCode(
            String draft,
            RagAnswerPolicyValidator.Validation validation) {
        if (draft == null || draft.isBlank()) {
            return RagAnswerOutcome.ReasonCode.EMPTY_DRAFT;
        }
        return switch (validation.citations().status()) {
            case MISSING_CITATION -> RagAnswerOutcome.ReasonCode.MISSING_CITATION;
            case OUT_OF_RANGE -> RagAnswerOutcome.ReasonCode.OUT_OF_RANGE_CITATION;
            case NO_PACKED_EVIDENCE -> RagAnswerOutcome.ReasonCode.NO_PACKED_EVIDENCE;
            case INDEX_VALID -> switch (validation.status()) {
                case MISSING_UNIT_CITATION -> RagAnswerOutcome.ReasonCode.MISSING_UNIT_CITATION;
                case MISSING_COMPARISON_SOURCE_CITATION ->
                    RagAnswerOutcome.ReasonCode.MISSING_COMPARISON_SOURCE_CITATION;
                default -> RagAnswerOutcome.ReasonCode.NONE;
            };
        };
    }

    public record FinalizedAnswer(
            String canonicalContent,
            RagCitationValidator.Validation validation,
            RagAnswerPolicyValidator.Validation policyValidation,
            RagAnswerOutcome outcome) {
    }

    private record PartialCanonicalization(
            String canonicalContent,
            RagAnswerPolicyValidator.Validation validation,
            int omittedUnitCount) {
    }
}

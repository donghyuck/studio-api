package studio.one.platform.ai.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Produces a deterministic, citation-valid evidence digest when model citation repair fails. */
final class InterpretiveEvidenceFallback {

    private static final int MAX_ITEMS = 3;
    private static final int MAX_EXCERPT_CHARS = 220;

    Optional<String> draft(PackedEvidenceSet evidenceSet) {
        if (evidenceSet == null || evidenceSet.evidence().isEmpty()) {
            return Optional.empty();
        }
        Map<String, EvidenceExcerpt> excerpts = new LinkedHashMap<>();
        for (PackedEvidenceSet.PackedEvidence evidence : evidenceSet.evidence()) {
            if (!"SOURCE_VERIFIED".equals(evidence.supportStatus())) {
                continue;
            }
            for (PackedEvidenceSet.SourceSpan span : evidence.sourceSpans()) {
                String text = bounded(span.exactText());
                if (text != null) {
                    excerpts.putIfAbsent(text, new EvidenceExcerpt(evidence.citationIndex(), text));
                    break;
                }
            }
            if (excerpts.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (excerpts.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder answer = new StringBuilder("문서에서 직접 확인된 근거는 다음과 같습니다: ");
        int index = 0;
        for (EvidenceExcerpt excerpt : excerpts.values()) {
            if (index++ > 0) {
                answer.append(" ");
            }
            answer.append("\"").append(excerpt.text()).append("\" [")
                    .append(excerpt.citationIndex()).append("]");
        }
        int firstCitation = excerpts.values().iterator().next().citationIndex();
        answer.append(" 확인 한계: 생성 답변이 인용 검증을 통과하지 못해 해석적 결론은 단정하지 않고, "
                + "검증된 문서 근거만 제공합니다. [").append(firstCitation).append("]");
        return Optional.of(answer.toString());
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_EXCERPT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_EXCERPT_CHARS).stripTrailing() + "…";
    }

    private record EvidenceExcerpt(int citationIndex, String text) {
    }
}

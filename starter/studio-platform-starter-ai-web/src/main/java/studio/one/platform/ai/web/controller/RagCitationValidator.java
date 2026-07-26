package studio.one.platform.ai.web.controller;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural citation validation. Semantic support remains represented separately
 * by each evidence item's source-verification status.
 */
public final class RagCitationValidator {

    private static final Pattern CITATION = Pattern.compile(
            "(?<!\\d)\\[\\s*(\\d{1,9}(?:\\s*,\\s*\\d{1,9})*)\\s*]");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```");

    public Validation validate(String content, PackedEvidenceSet evidenceSet) {
        String withoutCode = CODE_FENCE.matcher(content == null ? "" : content).replaceAll("");
        Matcher matcher = CITATION.matcher(withoutCode);
        Set<Integer> cited = new LinkedHashSet<>();
        Set<Integer> invalid = new LinkedHashSet<>();
        int evidenceCount = evidenceSet == null ? 0 : evidenceSet.evidence().size();
        while (matcher.find()) {
            for (String value : matcher.group(1).split("\\s*,\\s*")) {
                int index = Integer.parseInt(value);
                if (index < 1 || index > evidenceCount) {
                    invalid.add(index);
                } else {
                    cited.add(index);
                }
            }
        }
        Status status;
        if (evidenceCount == 0) {
            status = Status.NO_PACKED_EVIDENCE;
        } else if (!invalid.isEmpty()) {
            status = Status.OUT_OF_RANGE;
        } else if (cited.isEmpty()) {
            status = Status.MISSING_CITATION;
        } else {
            status = Status.INDEX_VALID;
        }
        return new Validation(status, Set.copyOf(cited), Set.copyOf(invalid));
    }

    public enum Status {
        INDEX_VALID,
        MISSING_CITATION,
        OUT_OF_RANGE,
        NO_PACKED_EVIDENCE
    }

    public record Validation(Status status, Set<Integer> citedIndexes, Set<Integer> invalidIndexes) {

        public boolean valid() {
            return status == Status.INDEX_VALID;
        }
    }
}

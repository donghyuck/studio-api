package studio.one.platform.ai.web.controller;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Mode-aware structural validation. It deliberately does not claim semantic
 * entailment between an answer and its evidence.
 */
public final class RagAnswerPolicyValidator {

    private static final Pattern CITATION = Pattern.compile(
            "(?<!\\d)\\[\\s*\\d{1,9}(?:\\s*,\\s*\\d{1,9})*\\s*]");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```");
    private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+).+");
    private static final Pattern STRICT_SENTENCE = Pattern.compile(
            ".*?[.!?。！？](?:\\s*(?<!\\d)\\[\\s*\\d{1,9}(?:\\s*,\\s*\\d{1,9})*\\s*])*"
                    + "(?=\\s+\\S|$)|.+$",
            Pattern.DOTALL);

    public Validation validate(
            String content,
            PackedEvidenceSet evidenceSet,
            ResolvedRagAnswerPolicy policy,
            RagCitationValidator citationValidator) {
        RagCitationValidator.Validation citations = citationValidator.validate(content, evidenceSet);
        if (evidenceSet == null || evidenceSet.evidence().isEmpty()) {
            return new Validation(Status.NO_PACKED_EVIDENCE, citations, 0, 0, List.of());
        }
        List<ValidationUnit> units = validationUnits(content, policy);
        int citedUnits = (int) units.stream().filter(ValidationUnit::cited).count();
        Status status;
        if (!citations.valid()) {
            status = Status.CITATION_INVALID;
        } else if (citedUnits < units.size()) {
            status = Status.MISSING_UNIT_CITATION;
        } else if (missingComparisonSourceCitation(evidenceSet, citations)) {
            status = Status.MISSING_COMPARISON_SOURCE_CITATION;
        } else {
            status = Status.STRUCTURE_VALID;
        }
        return new Validation(status, citations, units.size(), citedUnits, units);
    }

    private boolean missingComparisonSourceCitation(
            PackedEvidenceSet evidenceSet,
            RagCitationValidator.Validation citations) {
        if (!RagEvidenceCoverageRequirement.DOCUMENT_AND_EXTERNAL.name().equals(
                evidenceSet.diagnostics().get("coverageRequirement"))) {
            return false;
        }
        boolean hasDocument = evidenceSet.evidence().stream()
                .anyMatch(item -> "DOCUMENT".equals(item.origin()));
        boolean hasExternal = evidenceSet.evidence().stream()
                .anyMatch(item -> "INDEXED_WEB".equals(item.origin())
                        || "OFFICIAL_EXTERNAL".equals(item.origin()));
        if (!hasDocument || !hasExternal) {
            return false;
        }
        boolean citesDocument = evidenceSet.evidence().stream()
                .filter(item -> citations.citedIndexes().contains(item.citationIndex()))
                .anyMatch(item -> "DOCUMENT".equals(item.origin()));
        boolean citesExternal = evidenceSet.evidence().stream()
                .filter(item -> citations.citedIndexes().contains(item.citationIndex()))
                .anyMatch(item -> "INDEXED_WEB".equals(item.origin())
                        || "OFFICIAL_EXTERNAL".equals(item.origin()));
        return !citesDocument || !citesExternal;
    }

    private List<ValidationUnit> validationUnits(String content, ResolvedRagAnswerPolicy policy) {
        String normalized = CODE_FENCE.matcher(content == null ? "" : content).replaceAll("");
        List<ValidationUnit> units = new ArrayList<>();
        for (String paragraph : normalized.split("\\R\\s*\\R")) {
            String text = paragraph.strip();
            if (text.length() < 12 || text.startsWith("#") || text.matches("[-|:\\s]+")) {
                continue;
            }
            if (text.lines().anyMatch(line -> LIST_ITEM.matcher(line.strip()).matches())) {
                text.lines()
                        .map(String::strip)
                        .filter(line -> LIST_ITEM.matcher(line).matches())
                        .filter(line -> line.length() >= 12)
                        .map(line -> unit(UnitKind.LIST_ITEM, line))
                        .forEach(units::add);
                continue;
            }
            if (policy != null && policy.effectiveMode() == RagAnswerMode.STRICT_GROUNDED) {
                Matcher sentences = STRICT_SENTENCE.matcher(text);
                while (sentences.find()) {
                    String unit = sentences.group().strip();
                    if (unit.length() >= 12) {
                        units.add(unit(UnitKind.SENTENCE, unit));
                    }
                }
            } else {
                units.add(unit(UnitKind.PARAGRAPH, text));
            }
        }
        return List.copyOf(units);
    }

    private ValidationUnit unit(UnitKind kind, String text) {
        return new ValidationUnit(kind, text, CITATION.matcher(text).find());
    }

    public enum Status {
        STRUCTURE_VALID,
        CITATION_INVALID,
        NO_PACKED_EVIDENCE,
        MISSING_UNIT_CITATION,
        MISSING_COMPARISON_SOURCE_CITATION
    }

    public record Validation(
            Status status,
            RagCitationValidator.Validation citations,
            int unitCount,
            int citedUnitCount,
            List<ValidationUnit> units) {
        public Validation {
            units = units == null ? List.of() : List.copyOf(units);
        }

        public boolean valid() {
            return status == Status.STRUCTURE_VALID;
        }
    }

    public enum UnitKind {
        SENTENCE,
        PARAGRAPH,
        LIST_ITEM
    }

    public record ValidationUnit(UnitKind kind, String text, boolean cited) {
        public ValidationUnit {
            kind = kind == null ? UnitKind.PARAGRAPH : kind;
            text = text == null ? "" : text;
        }
    }
}

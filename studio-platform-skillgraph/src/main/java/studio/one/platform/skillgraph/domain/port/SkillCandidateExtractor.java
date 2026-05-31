package studio.one.platform.skillgraph.domain.port;

import java.util.List;

public interface SkillCandidateExtractor {

    List<ExtractedSkillTerm> extract(String text);

    record ExtractedSkillTerm(
            String term,
            String searchText,
            String skillType,
            String action,
            List<String> technology,
            String target,
            String evidenceText,
            String context,
            String difficulty,
            double confidence) {

        public ExtractedSkillTerm(String term, double confidence) {
            this(term, null, null, null, List.of(), null, null, null, null, confidence);
        }

        public ExtractedSkillTerm {
            if (term == null || term.isBlank()) {
                throw new IllegalArgumentException("term must not be blank");
            }
            term = term.trim();
            searchText = normalize(searchText);
            skillType = normalize(skillType);
            action = normalize(action);
            technology = technology == null ? List.of() : technology.stream()
                    .map(ExtractedSkillTerm::normalize)
                    .filter(value -> value != null)
                    .distinct()
                    .toList();
            target = normalize(target);
            evidenceText = normalize(evidenceText);
            context = normalize(context);
            difficulty = normalize(difficulty);
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        }

        private static String normalize(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }
}

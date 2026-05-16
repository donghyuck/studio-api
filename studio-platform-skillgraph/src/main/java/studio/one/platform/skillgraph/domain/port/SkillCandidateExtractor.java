package studio.one.platform.skillgraph.domain.port;

import java.util.List;

public interface SkillCandidateExtractor {

    List<ExtractedSkillTerm> extract(String text);

    record ExtractedSkillTerm(String term, double confidence) {
        public ExtractedSkillTerm {
            if (term == null || term.isBlank()) {
                throw new IllegalArgumentException("term must not be blank");
            }
            term = term.trim();
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        }
    }
}

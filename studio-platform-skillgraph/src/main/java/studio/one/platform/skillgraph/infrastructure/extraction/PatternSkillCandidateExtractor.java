package studio.one.platform.skillgraph.infrastructure.extraction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;

public class PatternSkillCandidateExtractor implements SkillCandidateExtractor {

    private static final Pattern TECH_TOKEN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9+#]*(?:[ .-]?[A-Z]?[A-Za-z0-9+#]+){0,3})\\b");
    private static final Pattern KOREAN_SKILL = Pattern.compile(
            "([가-힣A-Za-z0-9+#. ]{2,40})(?:\\s*(?:기술|역량|프레임워크|라이브러리|도구|플랫폼|아키텍처))");

    private final int maxTerms;

    public PatternSkillCandidateExtractor() {
        this(50);
    }

    public PatternSkillCandidateExtractor(int maxTerms) {
        this.maxTerms = Math.max(1, maxTerms);
    }

    @Override
    public List<ExtractedSkillTerm> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        collect(KOREAN_SKILL.matcher(text), terms, 1);
        collect(TECH_TOKEN.matcher(text), terms, 1);
        return terms.stream()
                .filter(this::looksLikeSkill)
                .limit(maxTerms)
                .map(term -> new ExtractedSkillTerm(term, 0.65d))
                .toList();
    }

    private void collect(Matcher matcher, Set<String> terms, int group) {
        while (matcher.find() && terms.size() < maxTerms) {
            String term = clean(matcher.group(group));
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("^[,.;:()\\[\\]{}]+", "")
                .replaceAll("[,.;:()\\[\\]{}]+$", "")
                .replaceAll("\\s+", " ");
    }

    private boolean looksLikeSkill(String term) {
        String normalized = term.toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 60) {
            return false;
        }
        if (List.of("the", "and", "for", "with", "from", "this", "that").contains(normalized)) {
            return false;
        }
        return normalized.chars().anyMatch(Character::isLetter);
    }
}

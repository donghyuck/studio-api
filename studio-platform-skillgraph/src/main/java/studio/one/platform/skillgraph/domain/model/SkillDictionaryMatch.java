package studio.one.platform.skillgraph.domain.model;

public record SkillDictionaryMatch(
        SkillDictionary skill,
        double score,
        SkillDictionaryMatchType type) {

    public SkillDictionaryMatch {
        if (skill == null) {
            throw new IllegalArgumentException("skill must not be null");
        }
        score = Math.max(0.0d, Math.min(1.0d, score));
        type = type == null ? SkillDictionaryMatchType.SIMILARITY : type;
    }
}

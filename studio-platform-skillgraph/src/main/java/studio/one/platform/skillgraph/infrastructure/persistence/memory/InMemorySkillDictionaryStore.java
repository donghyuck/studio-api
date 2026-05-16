package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

public class InMemorySkillDictionaryStore implements SkillDictionaryStore {

    private final Map<String, SkillDictionary> skills = new ConcurrentHashMap<>();
    private final Map<String, String> skillIdByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, String> skillIdByNormalizedAlias = new ConcurrentHashMap<>();
    private final Map<String, SkillAlias> aliases = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> embeddingsBySkillId = new ConcurrentHashMap<>();

    @Override
    public SkillDictionary save(SkillDictionary skill) {
        skills.put(skill.skillId(), skill);
        skillIdByNormalizedName.put(skill.normalizedName(), skill.skillId());
        return skill;
    }

    public SkillDictionary save(SkillDictionary skill, List<Double> embedding) {
        save(skill);
        if (embedding != null && !embedding.isEmpty()) {
            embeddingsBySkillId.put(skill.skillId(), List.copyOf(embedding));
        }
        return skill;
    }

    @Override
    public Optional<SkillDictionary> findById(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedName(String normalizedName) {
        return Optional.ofNullable(skillIdByNormalizedName.get(normalizedName))
                .map(skills::get);
    }

    @Override
    public Optional<SkillDictionary> findByNormalizedAlias(String normalizedAlias) {
        return Optional.ofNullable(skillIdByNormalizedAlias.get(normalizedAlias))
                .map(skills::get);
    }

    @Override
    public Optional<SkillDictionaryMatch> findNearestByEmbedding(List<Double> embedding, double minScore) {
        if (embedding == null || embedding.isEmpty()) {
            return Optional.empty();
        }
        return embeddingsBySkillId.entrySet().stream()
                .map(entry -> new SkillDictionaryMatch(skills.get(entry.getKey()),
                        cosine(embedding, entry.getValue()), SkillDictionaryMatchType.SIMILARITY))
                .filter(match -> match.skill() != null && match.score() >= minScore)
                .max(Comparator.comparingDouble(SkillDictionaryMatch::score));
    }

    @Override
    public SkillAlias saveAlias(SkillAlias alias) {
        aliases.put(alias.aliasId(), alias);
        skillIdByNormalizedAlias.put(alias.normalizedAlias(), alias.skillId());
        return alias;
    }

    @Override
    public List<SkillDictionary> search(String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int max = limit <= 0 ? 100 : limit;
        return skills.values().stream()
                .filter(skill -> query.isBlank()
                        || skill.normalizedName().contains(query)
                        || skill.name().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(SkillDictionary::name))
                .limit(max)
                .toList();
    }

    @Override
    public List<SkillVectorItem> findVectorItems(int limit) {
        int max = limit <= 0 ? 1000 : limit;
        return skills.values().stream()
                .filter(skill -> embeddingsBySkillId.containsKey(skill.skillId()))
                .sorted(Comparator.comparing(SkillDictionary::name))
                .limit(max)
                .map(skill -> new SkillVectorItem(
                        skill.skillId(),
                        skill.name(),
                        embeddingsBySkillId.get(skill.skillId()),
                        null,
                        skill.createdAt()))
                .toList();
    }

    private double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        if (size == 0) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < size; i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}

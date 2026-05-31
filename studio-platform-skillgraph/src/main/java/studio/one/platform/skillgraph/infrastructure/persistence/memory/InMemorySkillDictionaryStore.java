package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;
import studio.one.platform.skillgraph.domain.model.SkillVectorItem;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

public class InMemorySkillDictionaryStore implements SkillDictionaryStore {

    private final Map<String, SkillDictionary> skills = new ConcurrentHashMap<>();
    private final Map<String, String> skillIdByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, String> skillIdByNormalizedAlias = new ConcurrentHashMap<>();
    private final Map<String, SkillAlias> aliases = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> embeddingsBySkillId = new ConcurrentHashMap<>();
    private final Map<String, SkillEmbeddingMetadata> embeddingMetadataByKey = new ConcurrentHashMap<>();

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
            embeddingMetadataByKey.put(embeddingKey(skill.skillId(), "unknown", "unknown"), new SkillEmbeddingMetadata(
                    "unknown",
                    "unknown",
                    embedding.size(),
                    java.time.Instant.now()));
        }
        return skill;
    }

    @Override
    public List<SkillEmbeddingMetadata> findEmbeddingMetadataList(String skillId) {
        String prefix = normalize(skillId) + "|";
        return embeddingMetadataByKey.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public SkillDictionary saveEmbedding(String skillId, List<Double> embedding, String embeddingModel) {
        return saveEmbedding(skillId, null, embeddingModel, embedding);
    }

    @Override
    public SkillDictionary saveEmbedding(
            String skillId,
            String embeddingProvider,
            String embeddingModel,
            List<Double> embedding) {
        SkillDictionary skill = findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        if (embedding != null && !embedding.isEmpty()) {
            embeddingsBySkillId.put(skill.skillId(), List.copyOf(embedding));
            String provider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider;
            String model = embeddingModel == null || embeddingModel.isBlank() ? "unknown" : embeddingModel;
            embeddingMetadataByKey.put(embeddingKey(skill.skillId(), provider, model), new SkillEmbeddingMetadata(
                    provider,
                    model,
                    embedding.size(),
                    java.time.Instant.now()));
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
    public Page<SkillDictionary> search(String q, String status, String categoryId, Pageable pageable) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String statusFilter = status == null ? "" : status.trim();
        String categoryFilter = categoryId == null ? "" : categoryId.trim();
        List<SkillDictionary> filtered = skills.values().stream()
                .filter(skill -> query.isBlank()
                        || skill.normalizedName().contains(query)
                        || skill.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(skill -> statusFilter.isBlank() || statusFilter.equals(skill.status()))
                .filter(skill -> categoryFilter.isBlank() || categoryFilter.equals(skill.categoryId()))
                .sorted(dictionaryComparator(pageable.getSort()))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(
                filtered.subList(start, end),
                pageable,
                filtered.size()

        );

    }

    private Comparator<SkillDictionary> dictionaryComparator(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return Comparator.comparing(SkillDictionary::name, Comparator.nullsLast(String::compareToIgnoreCase));
        }
        Comparator<SkillDictionary> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<SkillDictionary> next = comparatorFor(order.getProperty());
            if (next == null) {
                continue;
            }
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator == null
                ? Comparator.comparing(SkillDictionary::name, Comparator.nullsLast(String::compareToIgnoreCase))
                : comparator;
    }

    private Comparator<SkillDictionary> comparatorFor(String property) {
        Comparator<String> strings = Comparator.nullsLast(String::compareToIgnoreCase);
        return switch (property) {
            case "skillId", "skill_id" -> Comparator.comparing(SkillDictionary::skillId, strings);
            case "name", "skillName", "skill_name" -> Comparator.comparing(SkillDictionary::name, strings);
            case "normalizedName", "normalized_name" -> Comparator.comparing(SkillDictionary::normalizedName, strings);
            case "status" -> Comparator.comparing(SkillDictionary::status, strings);
            case "categoryId", "category_id" -> Comparator.comparing(SkillDictionary::categoryId, strings);
            case "createdAt", "created_at" -> Comparator.comparing(SkillDictionary::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt", "updated_at" -> Comparator.comparing(SkillDictionary::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
    }

    @Override
    public List<SkillDictionary> findByCategoryId(String categoryId, int limit) {
        int max = limit <= 0 ? 100 : limit;
        return skills.values().stream()
                .filter(skill -> java.util.Objects.equals(categoryId, skill.categoryId()))
                .sorted(Comparator.comparing(SkillDictionary::name))
                .limit(max)
                .toList();
    }

    @Override
    public int countByCategoryId(String categoryId) {
        return (int) skills.values().stream()
                .filter(skill -> java.util.Objects.equals(categoryId, skill.categoryId()))
                .count();
    }

    @Override
    public List<SkillVectorItem> findVectorItems(int limit) {
        return findVectorItems(null, null, null, limit);
    }

    @Override
    public List<SkillVectorItem> findVectorItems(String embeddingProvider, String embeddingModel, Integer embeddingDimension, int limit) {
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? null : embeddingProvider.trim();
        String model = embeddingModel == null || embeddingModel.isBlank() ? null : embeddingModel.trim();
        return skills.values().stream()
                .filter(skill -> "ACTIVE".equalsIgnoreCase(skill.status()))
                .filter(skill -> hasEmbedding(skill.skillId(), provider, model, embeddingDimension))
                .sorted(Comparator.comparing(SkillDictionary::name))
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(skill -> new SkillVectorItem(
                        skill.skillId(),
                        skill.name(),
                        embeddingFor(skill.skillId(), provider, model),
                        model,
                        skill.createdAt()))
                .toList();
    }

    @Override
    public List<SkillDictionary> findMissingEmbeddingSkills(int limit) {
        return findMissingEmbeddingSkills(null, null, limit);
    }

    @Override
    public List<SkillDictionary> findMissingEmbeddingSkills(String embeddingProvider, String embeddingModel, int limit) {
        int max = limit <= 0 ? 100 : limit;
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? "unknown" : embeddingProvider;
        String model = embeddingModel == null || embeddingModel.isBlank() ? "unknown" : embeddingModel;
        return skills.values().stream()
                .filter(skill -> "ACTIVE".equalsIgnoreCase(skill.status()))
                .filter(skill -> !embeddingMetadataByKey.containsKey(embeddingKey(skill.skillId(), provider, model)))
                .sorted(Comparator.comparing(SkillDictionary::name))
                .limit(max)
                .toList();
    }

    @Override
    public int countMissingEmbeddingSkills() {
        return countMissingEmbeddingSkills(null, null);
    }

    @Override
    public int countMissingEmbeddingSkills(String embeddingProvider, String embeddingModel) {
        return findMissingEmbeddingSkills(embeddingProvider, embeddingModel, Integer.MAX_VALUE).size();
    }

    @Override
    public int updateCategory(List<String> skillIds, String categoryId) {
        int affected = 0;
        for (String skillId : skillIds) {
            SkillDictionary skill = skills.get(skillId);
            if (skill == null) {
                continue;
            }
            save(new SkillDictionary(skill.skillId(), skill.name(), skill.normalizedName(), categoryId,
                    skill.status(), skill.createdAt(), java.time.Instant.now()));
            affected++;
        }
        return affected;
    }

    @Override
    public int updateCategoryByCategoryIds(List<String> sourceCategoryIds, String targetCategoryId) {
        List<String> sources = sourceCategoryIds == null ? List.of() : sourceCategoryIds;
        int affected = 0;
        for (SkillDictionary skill : List.copyOf(skills.values())) {
            if (sources.contains(skill.categoryId())) {
                save(new SkillDictionary(skill.skillId(), skill.name(), skill.normalizedName(), targetCategoryId,
                        skill.status(), skill.createdAt(), java.time.Instant.now()));
                affected++;
            }
        }
        return affected;
    }

    @Override
    public int updateStatus(List<String> skillIds, String status) {
        int affected = 0;
        for (String skillId : skillIds) {
            SkillDictionary skill = skills.get(skillId);
            if (skill == null) {
                continue;
            }
            save(new SkillDictionary(skill.skillId(), skill.name(), skill.normalizedName(), skill.categoryId(),
                    status, skill.createdAt(), java.time.Instant.now()));
            affected++;
        }
        return affected;
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

    private String embeddingKey(String skillId, String provider, String model) {
        return normalize(skillId) + "|" + normalize(provider) + "|" + normalize(model);
    }

    private boolean hasEmbedding(String skillId, String provider, String model, Integer dimension) {
        if (!embeddingsBySkillId.containsKey(skillId)) {
            return false;
        }
        if (provider == null && model == null && (dimension == null || dimension <= 0)) {
            return true;
        }
        return embeddingMetadataByKey.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(normalize(skillId) + "|"))
                .map(Map.Entry::getValue)
                .anyMatch(metadata -> (provider == null || provider.equals(metadata.embeddingProvider()))
                        && (model == null || model.equals(metadata.embeddingModel()))
                        && (dimension == null || dimension <= 0 || dimension.equals(metadata.embeddingDimension())));
    }

    private List<Double> embeddingFor(String skillId, String provider, String model) {
        return embeddingsBySkillId.getOrDefault(skillId, List.of());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

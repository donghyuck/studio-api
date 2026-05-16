package studio.one.platform.skillgraph.application.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

@RequiredArgsConstructor
public class DefaultSkillDictionaryService implements SkillDictionaryService {

    private final SkillDictionaryStore store;

    @Override
    public List<SkillDictionaryView> search(String q, int limit) {
        return store.search(normalizeQuery(q), normalizeLimit(limit)).stream()
                .map(SkillDictionaryView::from)
                .toList();
    }

    @Override
    public SkillDictionaryView get(String skillId) {
        return store.findById(skillId)
                .map(SkillDictionaryView::from)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, SkillGraphLimits.MAX_SEARCH_LIMIT);
    }

    private String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() <= SkillGraphLimits.MAX_QUERY_LENGTH
                ? trimmed
                : trimmed.substring(0, SkillGraphLimits.MAX_QUERY_LENGTH);
    }
}

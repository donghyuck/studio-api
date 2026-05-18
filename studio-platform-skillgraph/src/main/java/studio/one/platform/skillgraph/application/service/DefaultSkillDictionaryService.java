package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

/**
 * 스킬 사전 조회 유스케이스 구현체.
 *
 * 주요 역할:
 * - Skill Dictionary 검색
 * - Skill 상세 조회
 * - alias 포함 검색 지원
 * - category/taxonomy 기반 조회
 *
 * 핵심 처리 흐름:
 * 1. 검색어 normalize
 * 2. exact/alias 기준 검색
 * 3. category/taxonomy 조건 적용
 * 4. 결과 정렬 및 반환
 *
 * Skill Extraction, Recommendation, Mapping 기능의
 * 기준 데이터 조회 계층으로 사용된다.
 *
 * @author donghyuck, son
 * @since 2026-05-17
 *
 *        <pre>
 *
 * &lt;&lt; 개정이력(Modification Information) &gt;&gt;
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-05-17  donghyuck, son: 최초 생성.
 *        </pre>
 */

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
    public SkillDictionaryView create(CreateSkillDictionaryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String name = requireText(command.name(), "name");
        String normalizedName = SkillCandidate.normalizeSkillTerm(
                command.normalizedName() == null || command.normalizedName().isBlank()
                        ? name
                        : command.normalizedName());
        store.findByNormalizedName(normalizedName)
                .ifPresent(existing -> {
                    throw new DuplicateSkillDictionaryException(normalizedName);
                });
        Instant now = Instant.now();
        SkillDictionary skill = new SkillDictionary(
                "skill_" + UUID.randomUUID(),
                name,
                normalizedName,
                normalize(command.categoryId()),
                normalize(command.status()),
                now,
                now);
        return SkillDictionaryView.from(store.save(skill));
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

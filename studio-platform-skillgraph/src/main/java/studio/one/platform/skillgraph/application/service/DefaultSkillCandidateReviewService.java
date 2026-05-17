package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;

/**
 * * 스킬 후보 검토 유스케이스 구현체.
 *
 * 주요 역할:
 * - 추출된 SkillCandidate 조회
 * - 후보 승인/거절 처리
 * - 기존 Skill의 alias 등록
 * - Skill Dictionary 품질 관리
 *
 * 핵심 처리 흐름:
 * 1. 검토 대상 SkillCandidate 조회
 * 2. 승인 시:
 * - 신규 Skill 생성 또는
 * - 기존 Skill alias 연결
 * 3. 검토 상태(APPROVED/REJECTED) 저장
 * 4. Skill Dictionary 반영
 *
 * 이 서비스는 자동 추출 결과를 사람이 검증하는 과정에서 사용되며, Skill Graph의 품질을 높이는 데 중요한 역할을 한다.
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
public class DefaultSkillCandidateReviewService implements SkillCandidateReviewService {

    private final SkillCandidateStore store;
    private final SkillDictionaryStore dictionaryStore;

    public DefaultSkillCandidateReviewService(SkillCandidateStore store) {
        this(store, null);
    }

    @Override
    public List<SkillCandidateView> search(SkillCandidateStatus status, String q, int limit) {
        return search(status, q, null, null, limit);
    }

    @Override
    public List<SkillCandidateView> search(SkillCandidateStatus status, String q, String sourceType, String sourceId,
            int limit) {
        return store
                .searchCandidates(status, normalizeQuery(q), normalizeSource(sourceType), normalizeSource(sourceId),
                        normalizeLimit(limit))
                .stream()
                .map(SkillCandidateView::from)
                .toList();
    }

    @Override
    public SkillCandidateView get(String candidateId) {
        return SkillCandidateView.from(find(candidateId));
    }

    @Override
    public SkillCandidateView review(String candidateId, SkillCandidateReviewCommand command) {
        if (command == null || command.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        SkillCandidate existing = find(candidateId);
        SkillCandidate candidate = existing
                .withStatus(command.status(), command.matchedSkillId(), command.reviewerNote(), Instant.now());
        String matchedSkillId = reflectReview(candidate);
        if (matchedSkillId != null && !matchedSkillId.equals(candidate.matchedSkillId())) {
            candidate = candidate.withStatus(candidate.status(), matchedSkillId, candidate.reviewerNote(),
                    Instant.now());
        }
        return SkillCandidateView.from(store.saveCandidate(candidate));
    }

    private SkillCandidate find(String candidateId) {
        return store.findCandidate(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill candidate: " + candidateId));
    }

    private String reflectReview(SkillCandidate candidate) {
        if (dictionaryStore == null) {
            return candidate.matchedSkillId();
        }
        if (candidate.status() == SkillCandidateStatus.APPROVED && candidate.matchedSkillId() == null) {
            SkillDictionary skill = dictionaryStore.findByNormalizedName(candidate.normalizedTerm())
                    .orElseGet(() -> dictionaryStore.save(new SkillDictionary(
                            "skd_" + UUID.randomUUID(),
                            candidate.term(),
                            candidate.normalizedTerm(),
                            null,
                            "ACTIVE",
                            candidate.createdAt(),
                            candidate.updatedAt())));
            return skill.skillId();
        }
        if (candidate.status() == SkillCandidateStatus.ALIAS_CANDIDATE && candidate.matchedSkillId() != null) {
            dictionaryStore.saveAlias(new SkillAlias(
                    "ska_" + UUID.randomUUID(),
                    candidate.matchedSkillId(),
                    candidate.term(),
                    candidate.normalizedTerm(),
                    candidate.updatedAt()));
        }
        return candidate.matchedSkillId();
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

    private String normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

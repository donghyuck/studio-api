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

@RequiredArgsConstructor
public class DefaultSkillCandidateReviewService implements SkillCandidateReviewService {

    private final SkillCandidateStore store;
    private final SkillDictionaryStore dictionaryStore;

    public DefaultSkillCandidateReviewService(SkillCandidateStore store) {
        this(store, null);
    }

    @Override
    public List<SkillCandidateView> search(SkillCandidateStatus status, String q, int limit) {
        return store.searchCandidates(status, normalizeQuery(q), normalizeLimit(limit)).stream()
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
        reflectReview(candidate);
        return SkillCandidateView.from(store.saveCandidate(candidate));
    }

    private SkillCandidate find(String candidateId) {
        return store.findCandidate(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill candidate: " + candidateId));
    }

    private void reflectReview(SkillCandidate candidate) {
        if (dictionaryStore == null) {
            return;
        }
        if (candidate.status() == SkillCandidateStatus.APPROVED && candidate.matchedSkillId() == null) {
            dictionaryStore.findByNormalizedName(candidate.normalizedTerm())
                    .orElseGet(() -> dictionaryStore.save(new SkillDictionary(
                            "skd_" + UUID.randomUUID(),
                            candidate.term(),
                            candidate.normalizedTerm(),
                            null,
                            "ACTIVE",
                            candidate.createdAt(),
                            candidate.updatedAt())));
            return;
        }
        if (candidate.status() == SkillCandidateStatus.ALIAS_CANDIDATE && candidate.matchedSkillId() != null) {
            dictionaryStore.saveAlias(new SkillAlias(
                    "ska_" + UUID.randomUUID(),
                    candidate.matchedSkillId(),
                    candidate.term(),
                    candidate.normalizedTerm(),
                    candidate.updatedAt()));
        }
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

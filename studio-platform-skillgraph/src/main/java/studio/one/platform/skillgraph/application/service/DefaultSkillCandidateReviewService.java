package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.SkillCandidateAutoApproveCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateBulkReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateAutoApproveResult;
import studio.one.platform.skillgraph.application.result.SkillCandidateAutoApproveSkip;
import studio.one.platform.skillgraph.application.result.SkillMatchedDictionaryView;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillAlias;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;

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

public class DefaultSkillCandidateReviewService implements SkillCandidateReviewService {

    private final SkillCandidateStore store;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillEmbeddingPort embeddingPort;

    public DefaultSkillCandidateReviewService(SkillCandidateStore store) {
        this(store, null, new NoOpSkillEmbeddingPort());
    }

    public DefaultSkillCandidateReviewService(SkillCandidateStore store, SkillDictionaryStore dictionaryStore) {
        this(store, dictionaryStore, new NoOpSkillEmbeddingPort());
    }

    public DefaultSkillCandidateReviewService(
            SkillCandidateStore store,
            SkillDictionaryStore dictionaryStore,
            SkillEmbeddingPort embeddingPort) {
        this.store = store;
        this.dictionaryStore = dictionaryStore;
        this.embeddingPort = embeddingPort == null ? new NoOpSkillEmbeddingPort() : embeddingPort;
    }

    @Override
    public Page<SkillCandidateView> search(
            SkillCandidateStatus status,
            String q,
            String sourceType,
            String sourceId,
            Pageable pageable) {
        return store
                .searchCandidates(status, normalizeQuery(q), normalizeSource(sourceType), normalizeSource(sourceId),
                        pageable)
                .map(this::toView);
    }

    @Override
    public SkillCandidateView get(String candidateId) {
        return toView(find(candidateId));
    }

    @Override
    public SkillCandidateView review(String candidateId, SkillCandidateReviewCommand command) {
        return review(candidateId, command, false);
    }

    private SkillCandidateView review(
            String candidateId,
            SkillCandidateReviewCommand command,
            boolean generateEmbedding) {
        if (command == null || command.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        SkillCandidate existing = find(candidateId);
        SkillCandidate candidate = existing
                .withStatus(command.status(), command.matchedSkillId(), command.reviewerNote(), Instant.now());
        String matchedSkillId = reflectReview(candidate, generateEmbedding);
        if (matchedSkillId != null && !matchedSkillId.equals(candidate.matchedSkillId())) {
            candidate = candidate.withStatus(candidate.status(), matchedSkillId, candidate.reviewerNote(),
                    Instant.now());
        }
        return toView(store.saveCandidate(candidate));
    }

    @Override
    public List<SkillCandidateView> reviewAll(SkillCandidateBulkReviewCommand command) {
        if (command == null || command.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (!isBulkReviewStatus(command.status())) {
            throw new IllegalArgumentException("Bulk review status must be APPROVED, REJECTED, or NOISE");
        }
        if (command.candidateIds() == null || command.candidateIds().isEmpty()) {
            throw new IllegalArgumentException("candidateIds must not be empty");
        }
        return new LinkedHashSet<>(command.candidateIds()).stream()
                .map(candidateId -> review(candidateId,
                        new SkillCandidateReviewCommand(command.status(), null, command.reviewerNote()),
                        command.generateEmbedding()))
                .toList();
    }

    @Override
    public SkillCandidateAutoApproveResult autoApprove(SkillCandidateAutoApproveCommand command) {
        if (command == null || command.candidateIds() == null || command.candidateIds().isEmpty()) {
            throw new IllegalArgumentException("candidateIds must not be empty");
        }
        double minConfidence = threshold(command.minConfidence());
        double minSimilarityScore = threshold(command.minSimilarityScore());
        List<SkillCandidateView> approved = new ArrayList<>();
        List<SkillCandidateAutoApproveSkip> skipped = new ArrayList<>();
        for (String candidateId : new LinkedHashSet<>(command.candidateIds())) {
            SkillCandidate candidate = find(candidateId);
            SkillCandidateView view = toView(candidate);
            String skipReason = autoApproveSkipReason(view, minConfidence, minSimilarityScore);
            if (skipReason != null) {
                skipped.add(new SkillCandidateAutoApproveSkip(
                        candidateId,
                        skipReason,
                        view.confidence(),
                        view.similarityScore()));
                continue;
            }
            approved.add(approve(candidate, command.reviewerNote(), command.generateEmbedding()));
        }
        return new SkillCandidateAutoApproveResult(
                command.candidateIds().size(),
                approved.size(),
                skipped.size(),
                approved,
                skipped);
    }

    private SkillCandidateView approve(SkillCandidate candidate, String reviewerNote, boolean generateEmbedding) {
        SkillCandidate approved = candidate.withStatus(
                SkillCandidateStatus.APPROVED,
                candidate.matchedSkillId(),
                autoApproveReviewerNote(candidate.reviewerNote(), reviewerNote),
                Instant.now());
        String matchedSkillId = reflectReview(approved, generateEmbedding);
        if (matchedSkillId != null && !matchedSkillId.equals(approved.matchedSkillId())) {
            approved = approved.withStatus(approved.status(), matchedSkillId, approved.reviewerNote(), Instant.now());
        }
        return toView(store.saveCandidate(approved));
    }

    private String autoApproveReviewerNote(String existingReviewerNote, String requestedReviewerNote) {
        if (existingReviewerNote == null || existingReviewerNote.isBlank()) {
            return requestedReviewerNote;
        }
        if (!existingReviewerNote.startsWith("similarity=")) {
            return requestedReviewerNote == null || requestedReviewerNote.isBlank()
                    ? existingReviewerNote
                    : requestedReviewerNote;
        }
        if (requestedReviewerNote == null || requestedReviewerNote.isBlank()) {
            return existingReviewerNote;
        }
        return existingReviewerNote + "; " + requestedReviewerNote.trim();
    }

    private String autoApproveSkipReason(
            SkillCandidateView candidate,
            double minConfidence,
            double minSimilarityScore) {
        if (candidate.status() == SkillCandidateStatus.APPROVED) {
            return "already approved";
        }
        if (candidate.confidence() < minConfidence) {
            return "confidence below threshold";
        }
        if (minSimilarityScore <= 0.0d) {
            return null;
        }
        if (candidate.similarityScore() == null) {
            return "similar skill is missing";
        }
        if (candidate.similarityScore() < minSimilarityScore) {
            return "similarity below threshold";
        }
        return null;
    }

    private double threshold(Double value) {
        if (value == null) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private boolean isBulkReviewStatus(SkillCandidateStatus status) {
        return status == SkillCandidateStatus.APPROVED
                || status == SkillCandidateStatus.REJECTED
                || status == SkillCandidateStatus.NOISE;
    }

    private SkillCandidateView toView(SkillCandidate candidate) {
        return SkillCandidateView.from(candidate, matchedSkill(candidate));
    }

    private SkillMatchedDictionaryView matchedSkill(SkillCandidate candidate) {
        if (dictionaryStore == null || candidate.matchedSkillId() == null) {
            return null;
        }
        SkillDictionary skill = dictionaryStore.findById(candidate.matchedSkillId()).orElse(null);
        if (skill == null) {
            return null;
        }
        SkillDictionaryMatchType matchType = SkillDictionaryMatchType.SIMILARITY;
        double score = similarityScore(candidate.reviewerNote());
        if (score < 0.0d) {
            SkillDictionaryMatch match = dictionaryStore.findMatchByNormalizedTerm(candidate.normalizedTerm())
                    .filter(result -> result.skill().skillId().equals(candidate.matchedSkillId()))
                    .orElse(null);
            matchType = match == null ? null : match.type();
            score = match == null ? 1.0d : match.score();
        }
        return SkillMatchedDictionaryView.from(skill, matchType, score);
    }

    private double similarityScore(String reviewerNote) {
        if (reviewerNote == null || !reviewerNote.startsWith("similarity=")) {
            return -1.0d;
        }
        try {
            String value = reviewerNote.substring("similarity=".length());
            int delimiter = value.indexOf(';');
            return Double.parseDouble(delimiter < 0 ? value : value.substring(0, delimiter));
        } catch (NumberFormatException ex) {
            return -1.0d;
        }
    }

    private SkillCandidate find(String candidateId) {
        return store.findCandidate(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill candidate: " + candidateId));
    }

    private String reflectReview(SkillCandidate candidate, boolean generateEmbedding) {
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
            if (generateEmbedding) {
                saveEmbedding(skill);
            }
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

    private void saveEmbedding(SkillDictionary skill) {
        List<Double> embedding = embeddingPort.embedSkill(skill.name());
        if (embedding != null && !embedding.isEmpty()) {
            dictionaryStore.saveEmbedding(skill.skillId(), embedding, null);
        }
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

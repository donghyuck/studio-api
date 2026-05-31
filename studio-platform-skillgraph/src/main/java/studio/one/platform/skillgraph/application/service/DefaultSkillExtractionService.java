package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.result.SkillMatchedDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;

/**
 * 텍스트 기반 스킬 추출 유스케이스 구현체.
 *
 *
 *
 * 주요 역할:
 *
 * - 입력 텍스트에서 스킬 후보 추출
 *
 * - Skill Dictionary 매칭
 *
 * - embedding 기반 유사 스킬 탐색
 *
 * - SkillCandidate 저장
 *
 *
 *
 * 핵심 처리 흐름:
 *
 * 1. 입력 텍스트 검증
 * 2. source chunk 저장
 * 3. SkillCandidateExtractor 실행
 * 4. 용어 normalize
 * 5. exact/alias 매칭 시도
 * 6. 실패 시 embedding 유사도 검색
 * 7. SkillCandidate 생성 또는 기존 후보 갱신
 *
 * 
 * 현재 구조는 패턴 기반 후보 추출에 의존하므로 단순 기술 키워드 수준 결과가 발생할 수 있다.
 *
 * 향후 개선 방향:z
 * - Token-aware chunking
 * - semantic chunking
 * - LLM structured extraction
 * - canonical skill generation
 * - evidence 기반 confidence scoring
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

public class DefaultSkillExtractionService implements SkillExtractionService {

    private final SkillCandidateStore store;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillEmbeddingPort embeddingPort;
    private final SkillMatchPolicy matchPolicy;
    private final SkillCandidateExtractor extractor;

    public DefaultSkillExtractionService(SkillCandidateStore store, SkillCandidateExtractor extractor) {
        this(store, null, extractor, new NoOpSkillEmbeddingPort(), SkillMatchPolicy.defaults());
    }

    public DefaultSkillExtractionService(
            SkillCandidateStore store,
            SkillDictionaryStore dictionaryStore,
            SkillCandidateExtractor extractor,
            SkillEmbeddingPort embeddingPort,
            SkillMatchPolicy matchPolicy) {
        this.store = store;
        this.dictionaryStore = dictionaryStore;
        this.extractor = extractor;
        this.embeddingPort = embeddingPort == null ? new NoOpSkillEmbeddingPort() : embeddingPort;
        this.matchPolicy = matchPolicy == null ? SkillMatchPolicy.defaults() : matchPolicy;

    }

    // @Override
    // protected List<SkillCandidateExtractor.ExtractedSkillTerm> recommendTerms(String text) {
    //     return extractor.extract(text);
    // }

    @Override
    public SkillExtractionResult extract(SkillExtractionCommand command) {
        return extract(command, true);
    }

    @Override
    public SkillExtractionResult dryRun(SkillExtractionCommand command) {
        return extract(command, false);
    }

    private SkillExtractionResult extract(SkillExtractionCommand command, boolean persist) {
        validate(command);

        Instant now = Instant.now();
        String sourceChunkId = persist ? "sch_" + UUID.randomUUID() : null;
        if (persist) {
            store.saveSourceChunk(new SkillSourceChunk(
                    sourceChunkId,
                    command.sourceType(),
                    command.sourceId(),
                    command.chunkId(),
                    excerpt(command.text()),
                    now));
        }

        List<SkillCandidateView> candidates = new ArrayList<>();
        for (SkillCandidateExtractor.ExtractedSkillTerm term : extractor.extract(command.text())) {
            String normalized = SkillCandidate.normalizeSkillTerm(term.term());
            SkillCandidate candidate = persist
                    ? store.findCandidateBySourceAndNormalizedTerm(
                            command.sourceType(),
                            command.sourceId(),
                            command.chunkId(),
                            normalized)
                            .map(existing -> existing.incrementOccurrence(now))
                            .orElseGet(() -> newCandidate(command, sourceChunkId, term, normalized, now))
                    : newCandidate(command, sourceChunkId, term, normalized, now);
            candidates.add(toView(persist ? store.saveCandidate(candidate) : candidate));
        }
        return new SkillExtractionResult(sourceChunkId, candidates.size(), candidates);
    }

    private void validate(SkillExtractionCommand command) {
        if (command == null || command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (command.text().length() > SkillGraphLimits.MAX_EXTRACTION_TEXT_LENGTH) {
            throw new IllegalArgumentException("text length must be <= " + SkillGraphLimits.MAX_EXTRACTION_TEXT_LENGTH);
        }
    }

    private SkillCandidate newCandidate(
            SkillExtractionCommand command,
            String sourceChunkId,
            SkillCandidateExtractor.ExtractedSkillTerm term,
            String normalized,
            Instant now) {
        Optional<SkillDictionaryMatch> match = findDictionaryMatch(term.term(), normalized, term.searchText());
        SkillCandidateStatus status = match
                .map(this::statusFor)
                .orElse(SkillCandidateStatus.PENDING);
        String matchedSkillId = match.map(result -> result.skill().skillId()).orElse(null);
        String reviewerNote = match
                .filter(result -> result.type() == SkillDictionaryMatchType.SIMILARITY)
                .map(result -> "similarity=" + result.score())
                .orElse(null);
        return new SkillCandidate(
                "skc_" + UUID.randomUUID(),
                sourceChunkId,
                command.sourceType(),
                command.sourceId(),
                term.term(),
                normalized,
                term.searchText(),
                term.skillType(),
                term.action(),
                term.technology(),
                term.target(),
                term.evidenceText(),
                term.context(),
                term.difficulty(),
                extractor.getClass().getSimpleName(),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                status,
                term.confidence(),
                1,
                matchedSkillId,
                reviewerNote,
                now,
                now);
    }

    private Optional<SkillDictionaryMatch> findDictionaryMatch(String term, String normalized, String searchText) {
        if (dictionaryStore == null) {
            return Optional.empty();
        }
        Optional<SkillDictionaryMatch> exactOrAlias = dictionaryStore.findMatchByNormalizedTerm(normalized);
        if (exactOrAlias.isPresent()) {
            return exactOrAlias;
        }
        List<Double> embedding = embeddingPort.embedSkill(embeddingText(term, searchText));
        if (embedding.isEmpty()) {
            return Optional.empty();
        }
        return dictionaryStore.findNearestByEmbedding(embedding, matchPolicy.aliasThreshold());
    }

    private SkillCandidateStatus statusFor(SkillDictionaryMatch match) {
        if (match.type() == SkillDictionaryMatchType.EXACT || match.type() == SkillDictionaryMatchType.ALIAS) {
            return SkillCandidateStatus.MATCHED;
        }
        if (match.score() >= matchPolicy.matchThreshold()) {
            return SkillCandidateStatus.MATCHED;
        }
        return SkillCandidateStatus.ALIAS_CANDIDATE;
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
        SkillDictionaryMatch match = findDictionaryMatch(candidate.term(), candidate.normalizedTerm(), candidate.searchText())
                .filter(result -> result.skill().skillId().equals(candidate.matchedSkillId()))
                .orElse(null);
        return SkillMatchedDictionaryView.from(
                skill,
                match == null ? SkillDictionaryMatchType.SIMILARITY : match.type(),
                match == null ? 1.0d : match.score());
    }

    private String excerpt(String text) {
        int maxLength = 2048;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String embeddingText(String term, String searchText) {
        return searchText == null || searchText.isBlank() ? term : searchText;
    }

}

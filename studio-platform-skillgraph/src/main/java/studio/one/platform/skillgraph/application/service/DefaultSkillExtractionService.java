package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatch;
import studio.one.platform.skillgraph.domain.model.SkillDictionaryMatchType;
import studio.one.platform.skillgraph.domain.model.SkillSourceChunk;
import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;

public class DefaultSkillExtractionService implements SkillExtractionService {

    private final SkillCandidateStore store;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillCandidateExtractor extractor;
    private final SkillEmbeddingPort embeddingPort;
    private final SkillMatchPolicy matchPolicy;

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

    @Override
    public SkillExtractionResult extract(SkillExtractionCommand command) {
        if (command == null || command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (command.text().length() > SkillGraphLimits.MAX_EXTRACTION_TEXT_LENGTH) {
            throw new IllegalArgumentException("text length must be <= " + SkillGraphLimits.MAX_EXTRACTION_TEXT_LENGTH);
        }

        Instant now = Instant.now();
        String sourceChunkId = "sch_" + UUID.randomUUID();
        store.saveSourceChunk(new SkillSourceChunk(
                sourceChunkId,
                command.sourceType(),
                command.sourceId(),
                command.chunkId(),
                command.text(),
                now));

        List<SkillCandidateView> candidates = new ArrayList<>();
        for (SkillCandidateExtractor.ExtractedSkillTerm term : extractor.extract(command.text())) {
            String normalized = SkillCandidate.normalizeSkillTerm(term.term());
            SkillCandidate candidate = store.findCandidateByNormalizedTerm(normalized)
                    .map(existing -> existing.incrementOccurrence(now))
                    .orElseGet(() -> newCandidate(command, sourceChunkId, term, normalized, now));
            candidates.add(SkillCandidateView.from(store.saveCandidate(candidate)));
        }
        return new SkillExtractionResult(sourceChunkId, candidates.size(), candidates);
    }

    private SkillCandidate newCandidate(
            SkillExtractionCommand command,
            String sourceChunkId,
            SkillCandidateExtractor.ExtractedSkillTerm term,
            String normalized,
            Instant now) {
        Optional<SkillDictionaryMatch> match = findDictionaryMatch(term.term(), normalized);
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
                status,
                term.confidence(),
                1,
                matchedSkillId,
                reviewerNote,
                now,
                now);
    }

    private Optional<SkillDictionaryMatch> findDictionaryMatch(String term, String normalized) {
        if (dictionaryStore == null) {
            return Optional.empty();
        }
        Optional<SkillDictionaryMatch> exactOrAlias = dictionaryStore.findMatchByNormalizedTerm(normalized);
        if (exactOrAlias.isPresent()) {
            return exactOrAlias;
        }
        List<Double> embedding = embeddingPort.embedSkill(term);
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
}

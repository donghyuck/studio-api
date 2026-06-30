package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.web.dto.ChatMessageDto;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;
import studio.one.platform.ai.web.dto.ChatRagRetrievalOptionsDto;
import studio.one.platform.ai.web.dto.ChatRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationCompareResponseDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationRequestDto;
import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

public class RagRetrievalEvaluationRunner {
    private final RagChatRetrievalService retrievalService;
    private final RagRetrievalEvaluationStore evaluationStore;

    public RagRetrievalEvaluationRunner(
            RagChatRetrievalService retrievalService,
            RagRetrievalEvaluationStore evaluationStore) {
        this.retrievalService = retrievalService;
        this.evaluationStore = evaluationStore;
    }

    public RagRetrievalEvaluationResponseDto evaluate(RagRetrievalEvaluationRequestDto request) {
        return evaluate(request, ProgressListener.noop());
    }

    public RagRetrievalEvaluationResponseDto evaluate(
            RagRetrievalEvaluationRequestDto request,
            ProgressListener progressListener) {
        ProgressListener progress = progressListener == null ? ProgressListener.noop() : progressListener;
        List<RagRetrievalEvaluationResponseDto.StrategyResult> strategies = new ArrayList<>();
        for (String strategy : request.strategies()) {
            strategies.add(evaluateStrategy(strategy, request, progress));
            progress.strategyCompleted(strategy);
        }
        return evaluationStore.save(new RagRetrievalEvaluationResponseDto(
                "reval-" + UUID.randomUUID(),
                Instant.now(),
                request.objectType(),
                request.objectId(),
                request.questionSetId(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.topK(),
                request.minScore(),
                strategies));
    }

    public RagRetrievalEvaluationCompareResponseDto compare(
            RagRetrievalEvaluationResponseDto before,
            RagRetrievalEvaluationResponseDto after) {
        Map<String, RagRetrievalEvaluationResponseDto.StrategyResult> beforeByStrategy = new LinkedHashMap<>();
        for (RagRetrievalEvaluationResponseDto.StrategyResult strategy : before.strategies()) {
            beforeByStrategy.put(strategy.strategy(), strategy);
        }
        List<RagRetrievalEvaluationCompareResponseDto.StrategyDelta> deltas = new ArrayList<>();
        for (RagRetrievalEvaluationResponseDto.StrategyResult afterStrategy : after.strategies()) {
            RagRetrievalEvaluationResponseDto.StrategyResult beforeStrategy = beforeByStrategy.get(afterStrategy.strategy());
            if (beforeStrategy == null) {
                continue;
            }
            deltas.add(new RagRetrievalEvaluationCompareResponseDto.StrategyDelta(
                    afterStrategy.strategy(),
                    beforeStrategy.questionCount(),
                    afterStrategy.questionCount(),
                    beforeStrategy.hitRate(),
                    afterStrategy.hitRate(),
                    afterStrategy.hitRate() - beforeStrategy.hitRate(),
                    beforeStrategy.mrr(),
                    afterStrategy.mrr(),
                    afterStrategy.mrr() - beforeStrategy.mrr(),
                    beforeStrategy.averageElapsedMs(),
                    afterStrategy.averageElapsedMs(),
                    afterStrategy.averageElapsedMs() - beforeStrategy.averageElapsedMs()));
        }
        return new RagRetrievalEvaluationCompareResponseDto(before.runId(), after.runId(), deltas);
    }

    private RagRetrievalEvaluationResponseDto.StrategyResult evaluateStrategy(
            String strategy,
            RagRetrievalEvaluationRequestDto request,
            ProgressListener progress) {
        List<RagRetrievalEvaluationResponseDto.QuestionResult> questions = new ArrayList<>();
        int hitCount = 0;
        double reciprocalRankSum = 0.0d;
        long elapsedSum = 0L;
        for (RagRetrievalEvaluationRequestDto.Question question : request.questions()) {
            progress.questionStarted(strategy, question.query());
            long started = System.nanoTime();
            var retrieval = retrievalService.retrieve(
                    chatRequest(request, question.query(), strategy),
                    question.query(),
                    request.objectType(),
                    request.objectId(),
                    effectiveTopK(request.topK()),
                    request.minScore() == null ? 0.0d : request.minScore(),
                    request.topK(),
                    false);
            long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            Integer firstRelevantRank = firstRelevantRank(question, retrieval.results());
            boolean hit = firstRelevantRank != null;
            if (hit) {
                hitCount++;
                reciprocalRankSum += 1.0d / firstRelevantRank;
            }
            elapsedSum += elapsedMs;
            questions.add(new RagRetrievalEvaluationResponseDto.QuestionResult(
                    question.query(),
                    hit,
                    firstRelevantRank,
                    elapsedMs,
                    resultItems(retrieval.results())));
            progress.questionCompleted(strategy, question.query());
        }
        int questionCount = request.questions().size();
        return new RagRetrievalEvaluationResponseDto.StrategyResult(
                strategy,
                questionCount,
                hitCount,
                questionCount == 0 ? 0.0d : hitCount / (double) questionCount,
                questionCount == 0 ? 0.0d : reciprocalRankSum / questionCount,
                questionCount == 0 ? 0.0d : elapsedSum / (double) questionCount,
                questions);
    }

    private ChatRagRequestDto chatRequest(
            RagRetrievalEvaluationRequestDto request,
            String query,
            String strategy) {
        return new ChatRagRequestDto(
                new ChatRequestDto(null, null, List.of(new ChatMessageDto("user", query)),
                        null, null, null, null, null, null, null),
                query,
                request.topK(),
                request.objectType(),
                request.objectId(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.topK(),
                request.minScore(),
                false,
                strategy,
                evaluationRetrievalOptions(request.retrievalOptions()));
    }

    private ChatRagRetrievalOptionsDto evaluationRetrievalOptions(ChatRagRetrievalOptionsDto options) {
        if (options == null) {
            return new ChatRagRetrievalOptionsDto(
                    null, null, null, null, null, null, null, false);
        }
        if (options.queryExpansionEnabled() != null) {
            return options;
        }
        return new ChatRagRetrievalOptionsDto(
                options.structureTopK(),
                options.ideaBlockTopK(),
                options.finalTopK(),
                options.minScore(),
                options.dedupe(),
                options.includeDebugChunks(),
                options.distilledScoreBoost(),
                false);
    }

    private Integer firstRelevantRank(
            RagRetrievalEvaluationRequestDto.Question question,
            List<RagSearchResult> results) {
        for (int index = 0; index < results.size(); index++) {
            if (matches(question, results.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean matches(RagRetrievalEvaluationRequestDto.Question question, RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        if (contains(question.expectedChunkIds(), text(metadata.get("chunkId")))) {
            return true;
        }
        if (contains(question.expectedDocumentChunkIds(), text(metadata.get("documentChunkId")))) {
            return true;
        }
        if (question.expectedContentContains() != null) {
            String content = result.content() == null ? "" : result.content();
            return question.expectedContentContains().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .anyMatch(content::contains);
        }
        return false;
    }

    private List<RagRetrievalEvaluationResponseDto.ResultItem> resultItems(List<RagSearchResult> results) {
        List<RagRetrievalEvaluationResponseDto.ResultItem> items = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            RagSearchResult result = results.get(index);
            Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
            items.add(new RagRetrievalEvaluationResponseDto.ResultItem(
                    index + 1,
                    text(metadata.get("chunkId")),
                    text(metadata.get("documentChunkId")),
                    result.documentId(),
                    result.score(),
                    metadataPreview(metadata)));
        }
        return items;
    }

    private Map<String, Object> metadataPreview(Map<String, Object> metadata) {
        Map<String, Object> preview = new LinkedHashMap<>();
        for (String key : List.of("strategy", "chunkType", "actualChunkingStrategy", "sectionTitle",
                "markdownDocumentId", "markdownRevisionId", "ideaBlockDistilled",
                "ideaBlockDistillationFingerprint", "ideaBlockDistilledFromChunkIds")) {
            Object value = metadata.get(key);
            if (value != null) {
                preview.put(key, value);
            }
        }
        return preview;
    }

    private boolean contains(List<String> expected, String actual) {
        return expected != null && actual != null && expected.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(actual::equals);
    }

    private int effectiveTopK(Integer topK) {
        return topK == null || topK < 1 ? 5 : Math.min(topK, 100);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text.trim();
    }

    public interface ProgressListener {
        void questionStarted(String strategy, String query);

        void questionCompleted(String strategy, String query);

        void strategyCompleted(String strategy);

        static ProgressListener noop() {
            return new ProgressListener() {
                @Override
                public void questionStarted(String strategy, String query) {
                }

                @Override
                public void questionCompleted(String strategy, String query) {
                }

                @Override
                public void strategyCompleted(String strategy) {
                }
            };
        }
    }
}

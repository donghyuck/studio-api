package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationResponseDto;

/**
 * Bounded fallback store used when JDBC is not available.
 */
public class InMemoryRagRetrievalEvaluationStore implements RagRetrievalEvaluationStore {

    private final int maxRuns;
    private final Map<String, RagRetrievalEvaluationResponseDto> runs = new LinkedHashMap<>();

    public InMemoryRagRetrievalEvaluationStore() {
        this(50);
    }

    public InMemoryRagRetrievalEvaluationStore(int maxRuns) {
        this.maxRuns = Math.max(1, maxRuns);
    }

    @Override
    public synchronized RagRetrievalEvaluationResponseDto save(RagRetrievalEvaluationResponseDto result) {
        runs.put(result.runId(), result);
        trim();
        return result;
    }

    @Override
    public synchronized Optional<RagRetrievalEvaluationResponseDto> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public synchronized List<RagRetrievalEvaluationResponseDto> list() {
        return runs.values().stream()
                .sorted(Comparator.comparing(RagRetrievalEvaluationResponseDto::createdAt).reversed())
                .toList();
    }

    @Override
    public synchronized List<RagRetrievalEvaluationResponseDto> listByQuestionSet(String questionSetId) {
        return runs.values().stream()
                .filter(result -> questionSetId != null && questionSetId.equals(result.questionSetId()))
                .sorted(Comparator.comparing(RagRetrievalEvaluationResponseDto::createdAt).reversed())
                .toList();
    }

    private void trim() {
        while (runs.size() > maxRuns) {
            String firstKey = new ArrayList<>(runs.keySet()).get(0);
            runs.remove(firstKey);
        }
    }
}

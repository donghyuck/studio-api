package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationQuestionSetDto;

/**
 * Bounded fallback store used when JDBC is not available.
 */
public class InMemoryRagRetrievalEvaluationQuestionSetStore implements RagRetrievalEvaluationQuestionSetStore {
    private final int maxQuestionSets;
    private final Map<String, RagRetrievalEvaluationQuestionSetDto> questionSets = new LinkedHashMap<>();

    public InMemoryRagRetrievalEvaluationQuestionSetStore() {
        this(100);
    }

    public InMemoryRagRetrievalEvaluationQuestionSetStore(int maxQuestionSets) {
        this.maxQuestionSets = Math.max(1, maxQuestionSets);
    }

    @Override
    public synchronized RagRetrievalEvaluationQuestionSetDto save(RagRetrievalEvaluationQuestionSetDto questionSet) {
        questionSets.put(questionSet.questionSetId(), questionSet);
        trim();
        return questionSet;
    }

    @Override
    public synchronized Optional<RagRetrievalEvaluationQuestionSetDto> find(String questionSetId) {
        return Optional.ofNullable(questionSets.get(questionSetId));
    }

    @Override
    public synchronized List<RagRetrievalEvaluationQuestionSetDto> list() {
        return questionSets.values().stream()
                .sorted(Comparator.comparing(RagRetrievalEvaluationQuestionSetDto::updatedAt).reversed())
                .toList();
    }

    private void trim() {
        while (questionSets.size() > maxQuestionSets) {
            String firstKey = new ArrayList<>(questionSets.keySet()).get(0);
            questionSets.remove(firstKey);
        }
    }
}

package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyHistoryDto;

public class InMemoryRagRetrievalPolicyHistoryStore implements RagRetrievalPolicyHistoryStore {

    private final int maxHistories;
    private final Map<String, RagRetrievalPolicyHistoryDto> histories = new LinkedHashMap<>();

    public InMemoryRagRetrievalPolicyHistoryStore() {
        this(1_000);
    }

    public InMemoryRagRetrievalPolicyHistoryStore(int maxHistories) {
        this.maxHistories = Math.max(1, maxHistories);
    }

    @Override
    public synchronized RagRetrievalPolicyHistoryDto save(RagRetrievalPolicyHistoryDto history) {
        RagRetrievalPolicyHistoryDto saved = history.createdAt() == null
                ? new RagRetrievalPolicyHistoryDto(
                        history.historyId(),
                        normalize(history.objectType()),
                        normalize(history.objectId()),
                        normalize(history.retrievalStrategy()),
                        normalize(history.reason()),
                        normalize(history.questionSetId()),
                        normalize(history.evaluationRunId()),
                        history.score(),
                        history.hitRate(),
                        history.mrr(),
                        history.averageElapsedMs(),
                        Instant.now())
                : history;
        histories.put(saved.historyId(), saved);
        trim();
        return saved;
    }

    @Override
    public synchronized List<RagRetrievalPolicyHistoryDto> list(String objectType, String objectId) {
        String normalizedObjectType = normalize(objectType);
        String normalizedObjectId = normalize(objectId);
        return histories.values().stream()
                .filter(history -> normalizedObjectType.equals(history.objectType())
                        && normalizedObjectId.equals(history.objectId()))
                .sorted(Comparator.comparing(RagRetrievalPolicyHistoryDto::createdAt).reversed())
                .limit(100)
                .toList();
    }

    private void trim() {
        while (histories.size() > maxHistories) {
            String firstKey = new ArrayList<>(histories.keySet()).get(0);
            histories.remove(firstKey);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

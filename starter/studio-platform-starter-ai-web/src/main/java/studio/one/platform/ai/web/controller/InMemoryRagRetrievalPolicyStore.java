package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyDto;

public class InMemoryRagRetrievalPolicyStore implements RagRetrievalPolicyStore {

    private final Map<String, RagRetrievalPolicyDto> policies = new LinkedHashMap<>();

    @Override
    public synchronized RagRetrievalPolicyDto save(RagRetrievalPolicyDto policy) {
        String key = key(policy.objectType(), policy.objectId());
        Instant now = Instant.now();
        RagRetrievalPolicyDto existing = policies.get(key);
        RagRetrievalPolicyDto saved = new RagRetrievalPolicyDto(
                normalize(policy.objectType()),
                normalize(policy.objectId()),
                normalize(policy.retrievalStrategy()),
                policy.retrievalOptions(),
                normalize(policy.questionSetId()),
                normalize(policy.evaluationRunId()),
                policy.score(),
                policy.hitRate(),
                policy.mrr(),
                policy.averageElapsedMs(),
                existing == null || existing.createdAt() == null ? now : existing.createdAt(),
                now);
        policies.put(key, saved);
        return saved;
    }

    @Override
    public synchronized Optional<RagRetrievalPolicyDto> find(String objectType, String objectId) {
        return Optional.ofNullable(policies.get(key(objectType, objectId)));
    }

    @Override
    public synchronized List<RagRetrievalPolicyDto> list() {
        return policies.values().stream()
                .sorted(Comparator.comparing(RagRetrievalPolicyDto::updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private String key(String objectType, String objectId) {
        return normalize(objectType).toLowerCase(Locale.ROOT) + ":" + normalize(objectId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

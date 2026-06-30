package studio.one.platform.ai.web.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageDto;
import studio.one.platform.ai.web.dto.RagRetrievalPolicyUsageSummaryDto;

public class InMemoryRagRetrievalPolicyUsageStore implements RagRetrievalPolicyUsageStore {

    private final int maxUsages;
    private final Map<String, RagRetrievalPolicyUsageDto> usages = new LinkedHashMap<>();

    public InMemoryRagRetrievalPolicyUsageStore() {
        this(1_000);
    }

    public InMemoryRagRetrievalPolicyUsageStore(int maxUsages) {
        this.maxUsages = Math.max(1, maxUsages);
    }

    @Override
    public synchronized RagRetrievalPolicyUsageDto save(RagRetrievalPolicyUsageDto usage) {
        RagRetrievalPolicyUsageDto saved = usage.createdAt() == null
                ? new RagRetrievalPolicyUsageDto(
                        usage.usageId(),
                        normalize(usage.objectType()),
                        normalize(usage.objectId()),
                        normalize(usage.retrievalStrategy()),
                        normalize(usage.questionSetId()),
                        normalize(usage.evaluationRunId()),
                        usage.topK(),
                        usage.minScore(),
                        usage.resultCount(),
                        usage.skippedChat(),
                        usage.elapsedMs(),
                        Instant.now())
                : usage;
        usages.put(saved.usageId(), saved);
        trim();
        return saved;
    }

    @Override
    public synchronized List<RagRetrievalPolicyUsageDto> list(String objectType, String objectId) {
        String normalizedObjectType = normalize(objectType);
        String normalizedObjectId = normalize(objectId);
        return usages.values().stream()
                .filter(usage -> normalizedObjectType.equals(usage.objectType())
                        && normalizedObjectId.equals(usage.objectId()))
                .sorted(Comparator.comparing(RagRetrievalPolicyUsageDto::createdAt).reversed())
                .limit(100)
                .toList();
    }

    @Override
    public synchronized RagRetrievalPolicyUsageSummaryDto summary(String objectType, String objectId) {
        List<RagRetrievalPolicyUsageDto> rows = list(objectType, objectId);
        if (rows.isEmpty()) {
            return new RagRetrievalPolicyUsageSummaryDto(normalize(objectType), normalize(objectId),
                    0, 0.0d, 0.0d, 0, null);
        }
        double averageResultCount = rows.stream().mapToInt(RagRetrievalPolicyUsageDto::resultCount).average().orElse(0.0d);
        double averageElapsedMs = rows.stream().mapToLong(RagRetrievalPolicyUsageDto::elapsedMs).average().orElse(0.0d);
        int skippedChatCount = (int) rows.stream().filter(RagRetrievalPolicyUsageDto::skippedChat).count();
        return new RagRetrievalPolicyUsageSummaryDto(
                normalize(objectType),
                normalize(objectId),
                rows.size(),
                averageResultCount,
                averageElapsedMs,
                skippedChatCount,
                rows.get(0).retrievalStrategy());
    }

    private void trim() {
        while (usages.size() > maxUsages) {
            String firstKey = new ArrayList<>(usages.keySet()).get(0);
            usages.remove(firstKey);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

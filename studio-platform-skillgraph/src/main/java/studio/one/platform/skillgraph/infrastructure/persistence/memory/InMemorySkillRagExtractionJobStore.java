package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;

public class InMemorySkillRagExtractionJobStore implements SkillRagExtractionJobStore {

    private final Map<String, SkillRagExtractionJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, SkillRagExtractionJobItem> items = new ConcurrentHashMap<>();

    @Override
    public SkillRagExtractionJob saveJob(SkillRagExtractionJob job) {
        jobs.put(job.jobId(), job);
        return job;
    }

    @Override
    public Optional<SkillRagExtractionJob> findJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public SkillRagExtractionJobItem saveItem(SkillRagExtractionJobItem item) {
        items.put(item.jobId() + "|" + item.chunkId(), item);
        return item;
    }

    @Override
    public List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit) {
        return items.values().stream()
                .filter(item -> item.jobId().equals(jobId))
                .sorted(Comparator.comparing(SkillRagExtractionJobItem::createdAt))
                .skip(Math.max(0, offset))
                .limit(limit <= 0 ? 100 : limit)
                .toList();
    }

    @Override
    public List<SkillRagExtractionJobItem> listItemsByStatus(
            String jobId,
            SkillRagExtractionItemStatus status,
            int limit) {
        return items.values().stream()
                .filter(item -> item.jobId().equals(jobId))
                .filter(item -> item.status() == status)
                .sorted(Comparator.comparing(SkillRagExtractionJobItem::createdAt))
                .limit(limit <= 0 ? 100 : limit)
                .toList();
    }
}

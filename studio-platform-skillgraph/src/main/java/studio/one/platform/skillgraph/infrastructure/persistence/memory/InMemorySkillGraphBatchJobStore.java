package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;

public class InMemorySkillGraphBatchJobStore implements SkillGraphBatchJobStore {

    private final Map<String, SkillGraphBatchJob> jobs = new ConcurrentHashMap<>();

    @Override
    public SkillGraphBatchJob save(SkillGraphBatchJob job) {
        jobs.put(job.jobId(), job);
        return job;
    }

    @Override
    public Optional<SkillGraphBatchJob> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Page<SkillGraphBatchJob> search(
            SkillGraphBatchJobType jobType,
            SkillGraphBatchJobStatus status,
            Pageable pageable) {
        var filtered = jobs.values().stream()
                .filter(job -> jobType == null || job.jobType() == jobType)
                .filter(job -> status == null || job.status() == status)
                .sorted(Comparator.comparing(SkillGraphBatchJob::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(new ArrayList<>(filtered.subList(start, end)), pageable, filtered.size());
    }
}

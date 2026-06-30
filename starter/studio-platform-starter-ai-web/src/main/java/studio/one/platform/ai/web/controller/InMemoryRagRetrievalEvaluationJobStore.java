package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationJobDto;

/**
 * Bounded fallback store used when JDBC is not available.
 */
public class InMemoryRagRetrievalEvaluationJobStore implements RagRetrievalEvaluationJobStore {
    private final int maxJobs;
    private final Map<String, RagRetrievalEvaluationJobDto> jobs = new LinkedHashMap<>();

    public InMemoryRagRetrievalEvaluationJobStore() {
        this(100);
    }

    public InMemoryRagRetrievalEvaluationJobStore(int maxJobs) {
        this.maxJobs = Math.max(1, maxJobs);
    }

    @Override
    public synchronized RagRetrievalEvaluationJobDto save(RagRetrievalEvaluationJobDto job) {
        jobs.put(job.jobId(), job);
        trim();
        return job;
    }

    @Override
    public synchronized Optional<RagRetrievalEvaluationJobDto> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public synchronized List<RagRetrievalEvaluationJobDto> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(RagRetrievalEvaluationJobDto::createdAt).reversed())
                .toList();
    }

    private void trim() {
        while (jobs.size() > maxJobs) {
            String firstKey = new ArrayList<>(jobs.keySet()).get(0);
            jobs.remove(firstKey);
        }
    }
}

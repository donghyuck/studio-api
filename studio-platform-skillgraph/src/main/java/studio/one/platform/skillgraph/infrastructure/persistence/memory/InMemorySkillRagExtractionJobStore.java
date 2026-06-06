package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;

public class InMemorySkillRagExtractionJobStore implements SkillRagExtractionJobStore {

    private final Map<String, SkillRagExtractionJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, SkillRagExtractionJobItem> items = new ConcurrentHashMap<>();
    private final Map<String, Lease> leases = new ConcurrentHashMap<>();

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
    public List<SkillRagExtractionJob> listJobs(
            SkillRagExtractionJobStatus status,
            String objectType,
            String objectId,
            int offset,
            int limit) {
        return jobs.values().stream()
                .filter(job -> status == null || job.status() == status)
                .filter(job -> objectType == null || objectType.equals(job.objectType()))
                .filter(job -> objectId == null || objectId.equals(job.objectId()))
                .sorted(Comparator.comparing(SkillRagExtractionJob::updatedAt).reversed()
                        .thenComparing(SkillRagExtractionJob::createdAt, Comparator.reverseOrder()))
                .skip(Math.max(0, offset))
                .limit(limit <= 0 ? 50 : limit)
                .toList();
    }

    @Override
    public Page<SkillRagExtractionJob> searchJobs(
            SkillRagExtractionJobStatus status,
            String objectType,
            String objectId,
            Pageable pageable) {
        List<SkillRagExtractionJob> filtered = jobs.values().stream()
                .filter(job -> status == null || job.status() == status)
                .filter(job -> objectType == null || objectType.equals(job.objectType()))
                .filter(job -> objectId == null || objectId.equals(job.objectId()))
                .sorted(Comparator.comparing(SkillRagExtractionJob::updatedAt).reversed()
                        .thenComparing(SkillRagExtractionJob::createdAt, Comparator.reverseOrder()))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), filtered.size()));
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
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

    @Override
    public Set<String> findSuccessfulChunkIds(
            String objectType,
            String objectId,
            String excludedJobId) {
        Set<String> matchingJobIds = jobs.values().stream()
                .filter(job -> excludedJobId == null || !excludedJobId.equals(job.jobId()))
                .filter(job -> objectType == null || objectType.equals(job.objectType()))
                .filter(job -> objectId == null || objectId.equals(job.objectId()))
                .map(SkillRagExtractionJob::jobId)
                .collect(Collectors.toSet());
        return items.values().stream()
                .filter(item -> matchingJobIds.contains(item.jobId()))
                .filter(item -> item.status() == SkillRagExtractionItemStatus.SUCCEEDED)
                .map(SkillRagExtractionJobItem::chunkId)
                .collect(Collectors.toSet());
    }

    @Override
    public synchronized boolean acquireLease(
            String jobId, String owner, Instant now, Duration leaseDuration, int maxAutoRetries) {
        SkillRagExtractionJob job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        Lease lease = leases.get(jobId);
        if (lease != null && lease.expiresAt().isAfter(now) && !lease.owner().equals(owner)) {
            return false;
        }
        int retries = lease == null ? 0 : lease.retryCount();
        if (retries >= Math.max(1, maxAutoRetries)) {
            return false;
        }
        leases.put(jobId, new Lease(owner, now.plus(leaseDuration), retries + 1));
        return true;
    }

    @Override
    public synchronized boolean renewLease(String jobId, String owner, Instant now, Duration leaseDuration) {
        Lease lease = leases.get(jobId);
        if (lease == null || !lease.owner().equals(owner)) {
            return false;
        }
        leases.put(jobId, new Lease(owner, now.plus(leaseDuration), lease.retryCount()));
        return true;
    }

    @Override
    public synchronized void releaseLease(String jobId, String owner) {
        Lease lease = leases.get(jobId);
        if (lease != null && lease.owner().equals(owner)) {
            leases.remove(jobId);
        }
    }

    @Override
    public List<String> findRecoverableJobIds(Instant now, int limit) {
        return jobs.values().stream()
                .filter(job -> job.status() == SkillRagExtractionJobStatus.READY
                        || job.status() == SkillRagExtractionJobStatus.RUNNING)
                .filter(job -> {
                    Lease lease = leases.get(job.jobId());
                    return lease == null || lease.expiresAt().isBefore(now);
                })
                .limit(Math.max(1, limit))
                .map(SkillRagExtractionJob::jobId)
                .toList();
    }

    private record Lease(String owner, Instant expiresAt, int retryCount) {
    }
}

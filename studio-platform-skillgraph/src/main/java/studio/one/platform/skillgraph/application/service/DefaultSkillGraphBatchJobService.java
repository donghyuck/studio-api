package studio.one.platform.skillgraph.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.exception.NotFoundException;
import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobService;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;

public class DefaultSkillGraphBatchJobService implements SkillGraphBatchJobService {

    private final SkillGraphBatchJobStore store;

    public DefaultSkillGraphBatchJobService(SkillGraphBatchJobStore store) {
        this.store = store;
    }

    @Override
    public SkillGraphBatchJobView get(String jobId) {
        String normalizedJobId = requireText(jobId, "jobId");
        return store.findById(normalizedJobId)
                .map(SkillGraphBatchJobView::from)
                .orElseThrow(() -> NotFoundException.of("skillGraphBatchJob", normalizedJobId));
    }

    @Override
    public Page<SkillGraphBatchJobView> search(
            SkillGraphBatchJobType jobType,
            SkillGraphBatchJobStatus status,
            Pageable pageable) {
        return store.search(jobType, status, pageable).map(SkillGraphBatchJobView::from);
    }

    private String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

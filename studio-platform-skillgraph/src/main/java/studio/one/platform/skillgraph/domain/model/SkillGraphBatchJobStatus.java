package studio.one.platform.skillgraph.domain.model;

public enum SkillGraphBatchJobStatus {
    CREATED,
    VALIDATING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELED
}

package studio.one.platform.skillgraph.domain.model;

/**
 *
 * Skill Dataset Import 작업 상태
 *
 */

public enum SkillDatasetImportJobStatus {

    /**
     * 작업 생성됨
     */
    CREATED,

    /**
     * 대기 중
     */
    QUEUED,

    /**
     * 실행 중
     */
    RUNNING,

    /**
     * 완료
     */

    COMPLETED,

    /**
     * 실패
     */
    FAILED,

    /**
     * 취소
     */
    CANCELLED

}
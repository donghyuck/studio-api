package studio.one.platform.skillgraph.infrastructure.extraction;

public class SkillCandidateExtractionException extends RuntimeException {

    public SkillCandidateExtractionException(String message) {
        super(message);
    }

    public SkillCandidateExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

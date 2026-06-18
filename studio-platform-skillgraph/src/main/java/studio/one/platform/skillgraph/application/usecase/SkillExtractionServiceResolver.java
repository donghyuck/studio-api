package studio.one.platform.skillgraph.application.usecase;

@FunctionalInterface
public interface SkillExtractionServiceResolver {

    SkillExtractionService resolve(String requestedMode);

    default String resolveMode(String requestedMode) {
        return requestedMode == null || requestedMode.isBlank()
                ? null
                : requestedMode.trim().toLowerCase(java.util.Locale.ROOT);
    }

    static SkillExtractionServiceResolver fixed(SkillExtractionService service) {
        return requestedMode -> service;
    }
}

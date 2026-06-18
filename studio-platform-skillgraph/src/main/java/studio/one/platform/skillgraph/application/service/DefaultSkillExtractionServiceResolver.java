package studio.one.platform.skillgraph.application.service;

import java.util.Locale;
import java.util.Objects;

import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionServiceResolver;

public final class DefaultSkillExtractionServiceResolver implements SkillExtractionServiceResolver {

    private final String defaultMode;
    private final SkillExtractionService regexService;
    private final SkillExtractionService llmService;

    public DefaultSkillExtractionServiceResolver(
            String defaultMode,
            SkillExtractionService regexService,
            SkillExtractionService llmService) {
        this.defaultMode = normalize(defaultMode);
        this.regexService = regexService;
        this.llmService = llmService;
    }

    @Override
    public SkillExtractionService resolve(String requestedMode) {
        String mode = resolveMode(requestedMode);
        SkillExtractionService service = switch (mode) {
            case "regex" -> regexService;
            case "llm" -> llmService;
            default -> throw new IllegalArgumentException("Unsupported candidateExtractorMode: " + requestedMode);
        };
        if (service == null) {
            throw new IllegalStateException("Skill extraction mode is not available: " + mode);
        }
        return service;
    }

    @Override
    public String resolveMode(String requestedMode) {
        return requestedMode == null || requestedMode.isBlank()
                ? defaultMode
                : normalize(requestedMode);
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "defaultMode").trim().toLowerCase(Locale.ROOT);
    }
}

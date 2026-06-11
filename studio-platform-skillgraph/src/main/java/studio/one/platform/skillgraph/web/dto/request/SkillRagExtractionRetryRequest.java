package studio.one.platform.skillgraph.web.dto.request;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionRetryMode;

public record SkillRagExtractionRetryRequest(
        SkillRagExtractionRetryMode mode) {

    public SkillRagExtractionRetryMode resolvedMode() {
        return mode == null ? SkillRagExtractionRetryMode.FAILED_ONLY : mode;
    }
}

package studio.one.platform.markdown.application;

public record MarkdownProcessingPlan(
        String requestedDocumentProfile,
        String resolvedDocumentProfile,
        String profileVersion,
        String resolutionReason,
        String costTier,
        MarkdownPipelineOptions effectiveOptions) {

    public static MarkdownProcessingPlan from(MarkdownPipelineOptions options) {
        String requested = options == null ? null : options.requestedDocumentProfile();
        String resolved = options == null ? null : options.resolvedDocumentProfile();
        MarkdownDocumentProfile profile = MarkdownDocumentProfile.parse(resolved);
        return new MarkdownProcessingPlan(
                requested,
                resolved,
                options == null ? null : options.documentProfileVersion(),
                "AUTO".equals(requested) ? "AUTO_BASELINE_RUNTIME_ANALYZER_MAY_ADAPT"
                        : requested == null ? "LEGACY_OPTIONS" : "CLIENT_SELECTED_PROFILE",
                profile == null ? "LEGACY" : profile.costTier(),
                options == null ? MarkdownPipelineOptions.none() : options);
    }
}

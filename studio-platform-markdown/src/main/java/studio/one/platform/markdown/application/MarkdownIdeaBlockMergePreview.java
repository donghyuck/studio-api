package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownIdeaBlockMergePreview(
        String documentId,
        String revisionId,
        boolean llmUsed,
        String llmProvider,
        String llmModel,
        List<ClusterPreview> clusters) {

    public record ClusterPreview(
            String clusterId,
            String clusterType,
            List<String> chunkIds,
            String status,
            String reason,
            String criticalQuestion,
            String trustedAnswer,
            List<String> keywords,
            List<String> tags,
            Object sourceEvidence,
            List<Object> sourceBlockRanges,
            String mergeReason,
            String previewText,
            String planId,
            String planFingerprint,
            boolean applicable,
            List<String> validationWarnings,
            List<String> mergedFromChunkIds) {
    }
}

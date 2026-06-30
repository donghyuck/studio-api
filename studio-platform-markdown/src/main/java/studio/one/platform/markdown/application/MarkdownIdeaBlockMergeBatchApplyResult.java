package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownIdeaBlockMergeBatchApplyResult(
        String documentId,
        String revisionId,
        List<Applied> applied,
        List<Failed> failed,
        MarkdownResumeResult pipelineResult) {

    public record Applied(
            String planId,
            String planFingerprint,
            String mergedChunkId,
            List<String> mergedFromChunkIds,
            int beforeChunkCount,
            int afterChunkCount) {
    }

    public record Failed(
            String planFingerprint,
            String errorMessage) {
    }
}

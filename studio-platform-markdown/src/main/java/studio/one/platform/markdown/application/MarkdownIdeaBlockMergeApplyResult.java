package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownIdeaBlockMergeApplyResult(
        String documentId,
        String revisionId,
        String planId,
        String planFingerprint,
        String mergedChunkId,
        List<String> mergedFromChunkIds,
        int beforeChunkCount,
        int afterChunkCount,
        MarkdownResumeResult pipelineResult) {
}

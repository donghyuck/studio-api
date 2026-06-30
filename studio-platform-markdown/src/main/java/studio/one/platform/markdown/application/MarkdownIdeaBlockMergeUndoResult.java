package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownIdeaBlockMergeUndoResult(
        String documentId,
        String revisionId,
        String mergedChunkId,
        String planFingerprint,
        List<String> restoredChunkIds,
        int beforeChunkCount,
        int afterChunkCount,
        MarkdownResumeResult pipelineResult) {
}

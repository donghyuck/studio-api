package studio.one.platform.markdown.application.port;

import java.util.function.Consumer;

import studio.one.platform.markdown.application.MarkdownPipelineProgress;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownRevision;

public interface MarkdownPipelinePort {

    void process(MarkdownRevision revision, boolean runChunking, boolean runRagIndex, boolean runSkillExtraction);

    default void process(MarkdownRevision revision, MarkdownPipelineOptions options) {
        MarkdownPipelineOptions effective = options == null ? MarkdownPipelineOptions.none() : options;
        process(revision, effective.runChunking(), effective.runRagIndex(), effective.runSkillExtraction());
    }

    default void process(MarkdownRevision revision, MarkdownPipelineOptions options,
            MarkdownPipelineStage fromStage, Consumer<MarkdownPipelineStage> stageCompleted) {
        process(revision, options);
        stageCompleted.accept(MarkdownPipelineStage.COMPLETED);
    }

    default int estimateChunkCount(MarkdownRevision revision, MarkdownPipelineOptions options) {
        if (revision == null || revision.markdownText() == null || revision.markdownText().isBlank()) {
            return 0;
        }
        int maxSize = options == null || options.chunkMaxSize() == null ? 1000 : options.chunkMaxSize();
        int overlap = options == null || options.chunkOverlap() == null ? 0 : options.chunkOverlap();
        int effectiveSize = Math.max(1, maxSize - Math.max(0, Math.min(overlap, maxSize - 1)));
        return Math.max(1, (int) Math.ceil((double) revision.markdownText().length() / effectiveSize));
    }

    default MarkdownPipelineProgress.RagProgress latestRagProgress(MarkdownRevision revision) {
        return null;
    }

    static MarkdownPipelinePort noop() {
        return (revision, runChunking, runRagIndex, runSkillExtraction) -> {
        };
    }
}

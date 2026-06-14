package studio.one.platform.markdown.application.port;

import java.util.function.Consumer;

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

    static MarkdownPipelinePort noop() {
        return (revision, runChunking, runRagIndex, runSkillExtraction) -> {
        };
    }
}

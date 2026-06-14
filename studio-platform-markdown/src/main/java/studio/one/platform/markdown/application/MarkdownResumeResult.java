package studio.one.platform.markdown.application;

import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownRevision;

public record MarkdownResumeResult(
        MarkdownDocument document,
        MarkdownRevision revision,
        MarkdownPipelineExecution pipeline,
        String resumedPhase,
        MarkdownPipelineStage resumedFrom) {
}

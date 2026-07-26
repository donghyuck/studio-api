package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.MarkdownPipelinePlan;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;

class MarkdownPipelinePlanTest {

    @Test
    void metadataAlwaysPrecedesEnabledDownstreamStages() {
        MarkdownPipelinePlan plan = MarkdownPipelinePlan.of(new MarkdownPipelineOptions(true, true, true));

        assertThat(plan.enabledStages()).containsExactly(
                MarkdownPipelineStage.METADATA_ENRICHMENT,
                MarkdownPipelineStage.CHUNKING,
                MarkdownPipelineStage.RAG_INDEX,
                MarkdownPipelineStage.SKILL_EXTRACTION,
                MarkdownPipelineStage.COMPLETED);
        assertThat(plan.next(MarkdownPipelineStage.METADATA_ENRICHMENT))
                .isEqualTo(MarkdownPipelineStage.CHUNKING);
    }

    @Test
    void legacyChunkingResumeDoesNotRerunMetadata() {
        MarkdownPipelinePlan plan = MarkdownPipelinePlan.of(new MarkdownPipelineOptions(true, true, false));

        assertThat(plan.shouldRunFrom(MarkdownPipelineStage.CHUNKING,
                MarkdownPipelineStage.METADATA_ENRICHMENT)).isFalse();
        assertThat(plan.shouldRunFrom(MarkdownPipelineStage.CHUNKING,
                MarkdownPipelineStage.RAG_INDEX)).isTrue();
    }
}

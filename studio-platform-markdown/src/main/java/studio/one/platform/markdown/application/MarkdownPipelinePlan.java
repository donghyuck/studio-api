package studio.one.platform.markdown.application;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.markdown.domain.MarkdownPipelineStage;

public final class MarkdownPipelinePlan {

    private static final List<MarkdownPipelineStage> ORDER = List.of(
            MarkdownPipelineStage.METADATA_ENRICHMENT,
            MarkdownPipelineStage.CHUNKING,
            MarkdownPipelineStage.RAG_INDEX,
            MarkdownPipelineStage.SKILL_EXTRACTION,
            MarkdownPipelineStage.COMPLETED);

    private final List<MarkdownPipelineStage> enabledStages;

    private MarkdownPipelinePlan(List<MarkdownPipelineStage> enabledStages) {
        this.enabledStages = List.copyOf(enabledStages);
    }

    public static MarkdownPipelinePlan of(MarkdownPipelineOptions options) {
        List<MarkdownPipelineStage> stages = new ArrayList<>();
        stages.add(MarkdownPipelineStage.METADATA_ENRICHMENT);
        if (options.runChunking()) {
            stages.add(MarkdownPipelineStage.CHUNKING);
        }
        if (options.runRagIndex()) {
            stages.add(MarkdownPipelineStage.RAG_INDEX);
        }
        if (options.runSkillExtraction()) {
            stages.add(MarkdownPipelineStage.SKILL_EXTRACTION);
        }
        stages.add(MarkdownPipelineStage.COMPLETED);
        return new MarkdownPipelinePlan(stages);
    }

    public List<MarkdownPipelineStage> enabledStages() {
        return enabledStages;
    }

    public MarkdownPipelineStage first() {
        return enabledStages.get(0);
    }

    public MarkdownPipelineStage lastEnabled() {
        return enabledStages.size() == 1
                ? MarkdownPipelineStage.COMPLETED
                : enabledStages.get(enabledStages.size() - 2);
    }

    public boolean enabled(MarkdownPipelineStage stage) {
        return enabledStages.contains(stage);
    }

    public MarkdownPipelineStage next(MarkdownPipelineStage completed) {
        int completedOrder = orderOf(completed);
        return enabledStages.stream()
                .filter(stage -> orderOf(stage) > completedOrder)
                .findFirst()
                .orElse(MarkdownPipelineStage.COMPLETED);
    }

    public MarkdownPipelineStage previous(MarkdownPipelineStage stage) {
        int stageOrder = orderOf(stage);
        MarkdownPipelineStage previous = null;
        for (MarkdownPipelineStage candidate : enabledStages) {
            if (orderOf(candidate) >= stageOrder) {
                break;
            }
            previous = candidate;
        }
        return previous;
    }

    public boolean shouldRunFrom(MarkdownPipelineStage fromStage, MarkdownPipelineStage candidate) {
        return enabled(candidate) && orderOf(candidate) >= orderOf(fromStage);
    }

    public boolean isAtOrAfter(MarkdownPipelineStage left, MarkdownPipelineStage right) {
        return orderOf(left) >= orderOf(right);
    }

    private static int orderOf(MarkdownPipelineStage stage) {
        int index = ORDER.indexOf(stage);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown pipeline stage: " + stage);
        }
        return index;
    }
}

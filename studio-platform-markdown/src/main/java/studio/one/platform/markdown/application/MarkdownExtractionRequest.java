package studio.one.platform.markdown.application;

public record MarkdownExtractionRequest(
        long attachmentId,
        MarkdownPipelineOptions pipelineOptions,
        boolean force,
        String requestedBy) {

    public MarkdownExtractionRequest {
        pipelineOptions = pipelineOptions == null ? MarkdownPipelineOptions.none() : pipelineOptions;
    }

    public MarkdownExtractionRequest(long attachmentId, boolean runChunking, boolean runRagIndex,
            boolean runSkillExtraction, boolean force, String requestedBy) {
        this(attachmentId, new MarkdownPipelineOptions(runChunking, runRagIndex, runSkillExtraction),
                force, requestedBy);
    }

    public boolean runChunking() {
        return pipelineOptions.runChunking();
    }

    public boolean runRagIndex() {
        return pipelineOptions.runRagIndex();
    }

    public boolean runSkillExtraction() {
        return pipelineOptions.runSkillExtraction();
    }
}

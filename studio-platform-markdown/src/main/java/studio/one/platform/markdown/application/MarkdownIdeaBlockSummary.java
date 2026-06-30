package studio.one.platform.markdown.application;

import java.util.List;
import java.util.Map;

public record MarkdownIdeaBlockSummary(
        String documentId,
        String revisionId,
        double coverage,
        int chunkCount,
        int ideaBlockCount,
        int fallbackCount,
        String qualityStatus,
        String requestedDocumentType,
        String detectedDocumentType,
        Double documentTypeConfidence,
        String documentTypeReason,
        String blockifyProfile,
        String ideaBlockSchemaVersion,
        Map<String, Integer> typedFieldCounts,
        Map<String, Double> typedFieldCoverage,
        int mergeCandidateCount,
        int similarityClusterCount,
        int embeddingMergeCandidateCount,
        int embeddingSimilarityClusterCount,
        Double embeddingSimilarityThreshold,
        Map<String, Integer> fallbackReasonCounts,
        List<Integer> missingSourceBlocks,
        List<String> rejectedReasons,
        List<MergeCandidateCluster> mergeCandidateClusters,
        List<MergeCandidateCluster> embeddingCandidateClusters,
        List<SampleIdeaBlock> samples) {

    public MarkdownIdeaBlockSummary(
            String documentId,
            String revisionId,
            double coverage,
            int chunkCount,
            int ideaBlockCount,
            int fallbackCount,
            String qualityStatus,
            int mergeCandidateCount,
            int similarityClusterCount,
            int embeddingMergeCandidateCount,
            int embeddingSimilarityClusterCount,
            Double embeddingSimilarityThreshold,
            Map<String, Integer> fallbackReasonCounts,
            List<Integer> missingSourceBlocks,
            List<String> rejectedReasons,
            List<MergeCandidateCluster> mergeCandidateClusters,
            List<MergeCandidateCluster> embeddingCandidateClusters,
            List<SampleIdeaBlock> samples) {
        this(documentId, revisionId, coverage, chunkCount, ideaBlockCount, fallbackCount, qualityStatus,
                null, null, null, null, null, null, Map.of(), Map.of(),
                mergeCandidateCount, similarityClusterCount, embeddingMergeCandidateCount,
                embeddingSimilarityClusterCount, embeddingSimilarityThreshold, fallbackReasonCounts,
                missingSourceBlocks, rejectedReasons, mergeCandidateClusters, embeddingCandidateClusters, samples);
    }

    public record MergeCandidateCluster(
            String clusterId,
            int size,
            Double maxScore,
            List<String> chunkIds) {
    }

    public record SampleIdeaBlock(
            String chunkId,
            String name,
            String criticalQuestion,
            String trustedAnswer,
            List<String> tags,
            List<String> keywords,
            String entityName,
            String entityType,
            Object sourceEvidence,
            Object sourceBlockRange,
            String sourceSectionId,
            Double confidence,
            String generatorModel,
            String promptVersion,
            String fingerprint,
            String schemaVersion,
            String requestedChunkingStrategy,
            String actualChunkingStrategy,
            String validationStatus,
            String fallbackReason,
            Object typedFields,
            Boolean mergeCandidate,
            String similarityClusterId,
            Integer similarityClusterSize,
            Double similarityMaxScore,
            String mergePolicy,
            String embeddingSimilarityClusterId,
            Integer embeddingSimilarityClusterSize,
            Double embeddingSimilarityMaxScore,
            Boolean embeddingMergeCandidate,
            String embeddingMergePolicy) {
    }
}

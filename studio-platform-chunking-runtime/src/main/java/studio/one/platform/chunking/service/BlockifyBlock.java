package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Map;

public record BlockifyBlock(
        String name,
        String title,
        String criticalQuestion,
        String trustedAnswer,
        List<String> keywords,
        List<String> tags,
        String entityName,
        String entityType,
        List<BlockifySourceEvidence> sourceEvidence,
        SourceBlockRange sourceBlockRange,
        String sourceSectionId,
        double confidence,
        Map<String, Object> typedFields) {

    public BlockifyBlock(
            String name,
            String title,
            String criticalQuestion,
            String trustedAnswer,
            List<String> keywords,
            List<String> tags,
            String entityName,
            String entityType,
            List<BlockifySourceEvidence> sourceEvidence,
            SourceBlockRange sourceBlockRange,
            String sourceSectionId,
            double confidence) {
        this(name, title, criticalQuestion, trustedAnswer, keywords, tags, entityName, entityType, sourceEvidence,
                sourceBlockRange, sourceSectionId, confidence, Map.of());
    }

    public BlockifyBlock(
            String title,
            String question,
            String answer,
            List<String> keywords,
            List<String> tags,
            List<BlockifySourceEvidence> sourceEvidence,
            double confidence) {
        this(title, title, question, answer, keywords, tags, null, null, sourceEvidence, null, null, confidence,
                Map.of());
    }

    public String question() {
        return criticalQuestion;
    }

    public String answer() {
        return trustedAnswer;
    }

    public record SourceBlockRange(Integer start, Integer end) {
    }
}

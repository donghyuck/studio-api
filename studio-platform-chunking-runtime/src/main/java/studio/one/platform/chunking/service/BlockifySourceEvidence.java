package studio.one.platform.chunking.service;

import java.util.List;

public record BlockifySourceEvidence(
        String text,
        Integer normalizedBlockIndex,
        Integer startOffset,
        Integer endOffset,
        Integer page,
        Integer slide,
        List<String> headingPath,
        String sourceSectionId,
        List<String> sourceBlockIndexes) {
}

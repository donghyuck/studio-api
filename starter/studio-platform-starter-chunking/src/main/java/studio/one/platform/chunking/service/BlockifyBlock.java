package studio.one.platform.chunking.service;

import java.util.List;

public record BlockifyBlock(
        String title,
        String question,
        String answer,
        List<String> keywords,
        List<String> tags,
        List<BlockifySourceEvidence> sourceEvidence,
        double confidence) {
}

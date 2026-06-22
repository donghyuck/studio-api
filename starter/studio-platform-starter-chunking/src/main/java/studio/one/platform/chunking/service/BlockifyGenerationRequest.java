package studio.one.platform.chunking.service;

import java.util.List;

import studio.one.platform.chunking.core.NormalizedBlock;

public record BlockifyGenerationRequest(
        String sourceDocumentId,
        String sectionId,
        String headingPath,
        List<NormalizedBlock> blocks,
        String promptVersion,
        String generatorModel,
        double temperature,
        double topP,
        int maxBlocksPerSection) {
}

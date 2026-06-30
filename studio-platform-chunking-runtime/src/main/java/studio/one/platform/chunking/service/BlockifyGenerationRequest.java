package studio.one.platform.chunking.service;

import java.util.List;

import studio.one.platform.chunking.core.NormalizedBlock;

public record BlockifyGenerationRequest(
        String sourceDocumentId,
        String sectionId,
        String headingPath,
        List<NormalizedBlock> blocks,
        String promptVersion,
        String llmProvider,
        String llmModel,
        String generatorModel,
        double temperature,
        double topP,
        Boolean piiMaskingEnabled,
        int maxBlocksPerSection,
        BlockifyDocumentType documentType,
        String blockifyProfile,
        String ideaBlockSchemaVersion) {

    public BlockifyGenerationRequest(
            String sourceDocumentId,
            String sectionId,
            String headingPath,
            List<NormalizedBlock> blocks,
            String promptVersion,
            String llmProvider,
            String llmModel,
            String generatorModel,
            double temperature,
            double topP,
            Boolean piiMaskingEnabled,
            int maxBlocksPerSection) {
        this(sourceDocumentId, sectionId, headingPath, blocks, promptVersion, llmProvider, llmModel, generatorModel,
                temperature, topP, piiMaskingEnabled, maxBlocksPerSection, BlockifyDocumentType.GENERAL, null, null);
    }
}

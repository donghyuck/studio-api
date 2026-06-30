package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Set;

public interface BlockifyPiiMaskingPort {

    MaskedRequest mask(BlockifyGenerationRequest request);

    String deanonymize(String text, MaskedRequest maskedRequest);

    default BlockifyBlock deanonymize(BlockifyBlock block, MaskedRequest maskedRequest) {
        if (block == null) {
            return null;
        }
        List<BlockifySourceEvidence> evidence = block.sourceEvidence() == null ? List.of()
                : block.sourceEvidence().stream()
                        .map(value -> new BlockifySourceEvidence(
                                deanonymize(value.text(), maskedRequest),
                                value.normalizedBlockIndex(),
                                value.startOffset(),
                                value.endOffset(),
                                value.page(),
                                value.slide(),
                                value.headingPath(),
                                value.sourceSectionId(),
                                value.sourceBlockIndexes()))
                        .toList();
        return new BlockifyBlock(
                deanonymize(block.title(), maskedRequest),
                deanonymize(block.question(), maskedRequest),
                deanonymize(block.answer(), maskedRequest),
                deanonymizeList(block.keywords(), maskedRequest),
                deanonymizeList(block.tags(), maskedRequest),
                evidence,
                block.confidence());
    }

    private List<String> deanonymizeList(List<String> values, MaskedRequest maskedRequest) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> deanonymize(value, maskedRequest))
                .distinct()
                .toList();
    }

    record MaskedRequest(
            BlockifyGenerationRequest request,
            java.util.Map<String, String> replacements,
            int entityCount,
            Set<String> entityTypes) {
    }
}

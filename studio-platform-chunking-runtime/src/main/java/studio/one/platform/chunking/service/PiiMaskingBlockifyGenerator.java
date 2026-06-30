package studio.one.platform.chunking.service;

import java.util.List;
import java.util.Objects;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;

public class PiiMaskingBlockifyGenerator implements BlockifyGenerator {

    private final BlockifyGenerator delegate;
    private final BlockifyPiiMaskingPort maskingPort;
    private final ChunkingProperties.BlockifyPiiMaskingProperties properties;

    public PiiMaskingBlockifyGenerator(
            BlockifyGenerator delegate,
            BlockifyPiiMaskingPort maskingPort,
            ChunkingProperties.BlockifyPiiMaskingProperties properties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.maskingPort = Objects.requireNonNull(maskingPort, "maskingPort");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public List<BlockifyBlock> generate(BlockifyGenerationRequest request) {
        if (Boolean.FALSE.equals(request.piiMaskingEnabled())) {
            return delegate.generate(request);
        }
        BlockifyPiiMaskingPort.MaskedRequest maskedRequest;
        try {
            maskedRequest = maskingPort.mask(request);
        } catch (RuntimeException ex) {
            throw new BlockifyPiiMaskingException("Blockify PII masking failed", ex,
                    properties.failPipelineOnFailure());
        }
        List<BlockifyBlock> blocks = delegate.generate(maskedRequest.request());
        return blocks.stream()
                .map(block -> maskingPort.deanonymize(block, maskedRequest))
                .toList();
    }
}

package studio.one.platform.chunking.service;

import java.util.List;

public interface BlockifyGenerator {

    List<BlockifyBlock> generate(BlockifyGenerationRequest request);
}

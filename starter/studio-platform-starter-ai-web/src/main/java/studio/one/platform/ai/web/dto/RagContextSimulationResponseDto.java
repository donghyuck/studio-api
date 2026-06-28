package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagContextSimulationResponseDto(
        RagChunkingSimulationResponseDto.TokenizerStatusDto tokenizer,
        List<RagContextSimulationChunkDto> chunks,
        List<RagContextSimulationChunkDto> retrievedChunks,
        Integer usedTokens,
        List<String> warnings) {
}

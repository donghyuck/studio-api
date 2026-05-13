package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class RagChunkPreviewResponseDto {

    private final List<RagChunkPreviewItemDto> chunks;

    private final int totalChunks;

    private final int totalChars;

    private final String strategy;

    private final int maxSize;

    private final int overlap;

    private final String unit;

    private final List<String> warnings;

    @JsonCreator
    public RagChunkPreviewResponseDto(
            @JsonProperty("chunks") List<RagChunkPreviewItemDto> chunks,
            @JsonProperty("totalChunks") int totalChunks,
            @JsonProperty("totalChars") int totalChars,
            @JsonProperty("strategy") String strategy,
            @JsonProperty("maxSize") int maxSize,
            @JsonProperty("overlap") int overlap,
            @JsonProperty("unit") String unit,
            @JsonProperty("warnings") List<String> warnings
    ) {
        this.chunks = chunks;
        this.totalChunks = totalChunks;
        this.totalChars = totalChars;
        this.strategy = strategy;
        this.maxSize = maxSize;
        this.overlap = overlap;
        this.unit = unit;
        this.warnings = warnings;
    }

    public List<RagChunkPreviewItemDto> chunks() {
        return chunks;
    }

    public List<RagChunkPreviewItemDto> getChunks() {
        return chunks;
    }

    public int totalChunks() {
        return totalChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int totalChars() {
        return totalChars;
    }

    public int getTotalChars() {
        return totalChars;
    }

    public String strategy() {
        return strategy;
    }

    public String getStrategy() {
        return strategy;
    }

    public int maxSize() {
        return maxSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int overlap() {
        return overlap;
    }

    public int getOverlap() {
        return overlap;
    }

    public String unit() {
        return unit;
    }

    public String getUnit() {
        return unit;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> getWarnings() {
        return warnings;
    }

}
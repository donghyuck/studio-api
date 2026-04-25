package studio.one.platform.ai.core.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class TextChunkContractTest {

    @Test
    void legacyChunkContractsAreDeprecatedWithoutRemoval() {
        Deprecated chunkDeprecated = TextChunk.class.getAnnotation(Deprecated.class);
        Deprecated chunkerDeprecated = TextChunker.class.getAnnotation(Deprecated.class);

        assertThat(chunkDeprecated).isNotNull();
        assertThat(chunkDeprecated.since()).isEqualTo("2.x");
        assertThat(chunkDeprecated.forRemoval()).isFalse();
        assertThat(chunkerDeprecated).isNotNull();
        assertThat(chunkerDeprecated.since()).isEqualTo("2.x");
        assertThat(chunkerDeprecated.forRemoval()).isFalse();
    }
}

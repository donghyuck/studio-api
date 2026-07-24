package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

class AutomaticChunkingStrategyResolverTest {

    private final AutomaticChunkingStrategyResolver resolver = new AutomaticChunkingStrategyResolver();

    @Test
    void selectsStructureBasedForMultipleNormalizedContentBlocks() {
        NormalizedDocument document = document(List.of(
                block(NormalizedBlockType.HEADING, "Chapter", 0),
                block(NormalizedBlockType.PARAGRAPH, "First paragraph", 1),
                block(NormalizedBlockType.PARAGRAPH, "Second paragraph", 2)));

        AutomaticChunkingStrategyResolver.Selection selection = resolver.resolve(document, null);

        assertThat(selection.strategy()).isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
        assertThat(selection.mode()).isEqualTo("AUTO");
        assertThat(selection.reason()).isEqualTo("STRUCTURED_BLOCKS_AVAILABLE");
    }

    @Test
    void selectsStructureBasedForSingleLayoutBlock() {
        NormalizedDocument document = document(List.of(block(NormalizedBlockType.TABLE, "A | B", 0)));

        assertThat(resolver.resolve(document, null).strategy())
                .isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
    }

    @Test
    void selectsRecursiveForPlainTextFallbackBlock() {
        NormalizedDocument document = document(List.of(
                block(NormalizedBlockType.DOCUMENT, "Plain fallback text", 0)));

        AutomaticChunkingStrategyResolver.Selection selection = resolver.resolve(document, null);

        assertThat(selection.strategy()).isEqualTo(ChunkingStrategyType.RECURSIVE);
        assertThat(selection.reason()).isEqualTo("PLAIN_TEXT_ONLY");
    }

    @Test
    void selectsRecursiveForMultipleUnstructuredFallbackBlocks() {
        NormalizedDocument document = document(List.of(
                block(NormalizedBlockType.DOCUMENT, "First fallback fragment", 0),
                block(NormalizedBlockType.UNKNOWN, "Second fallback fragment", 1)));

        assertThat(resolver.resolve(document, null).strategy())
                .isEqualTo(ChunkingStrategyType.RECURSIVE);
    }

    @Test
    void selectsStructureBasedForPageReferencedFallbackBlocks() {
        NormalizedDocument document = document(List.of(
                NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, "First page")
                        .id("block-0")
                        .page(1)
                        .order(0)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.DOCUMENT, "Second page")
                        .id("block-1")
                        .page(2)
                        .order(1)
                        .build()));

        assertThat(resolver.resolve(document, null).strategy())
                .isEqualTo(ChunkingStrategyType.STRUCTURE_BASED);
    }

    @Test
    void preservesExplicitStrategy() {
        NormalizedDocument document = document(List.of(
                block(NormalizedBlockType.PARAGRAPH, "First paragraph", 0),
                block(NormalizedBlockType.PARAGRAPH, "Second paragraph", 1)));

        AutomaticChunkingStrategyResolver.Selection selection = resolver.resolve(document, "fixed-size");

        assertThat(selection.strategy()).isEqualTo(ChunkingStrategyType.FIXED_SIZE);
        assertThat(selection.mode()).isEqualTo("EXPLICIT");
        assertThat(selection.reason()).isEqualTo("EXPLICIT_REQUEST");
    }

    private NormalizedDocument document(List<NormalizedBlock> blocks) {
        return NormalizedDocument.builder("doc-1")
                .plainText("document text")
                .blocks(blocks)
                .build();
    }

    private NormalizedBlock block(NormalizedBlockType type, String text, int order) {
        return NormalizedBlock.builder(type, text)
                .id("block-" + order)
                .order(order)
                .build();
    }
}

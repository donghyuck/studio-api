package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagSearchResult;

class RagDocumentOverviewAssemblerTest {

    private final RagDocumentOverviewAssembler assembler = new RagDocumentOverviewAssembler();

    @Test
    void reconstructsAllOrderedChunksAndRemovesConfiguredOverlap() {
        RagDocumentOverviewAssembler.Assembly assembly = assembler.assemble(List.of(
                result(2, 10, 16, "dthree"),
                result(0, 0, 5, "first"),
                result(1, 4, 11, "tsecond")), 10_000, true);

        assertThat(assembly.context())
                .contains("문서 제목: Sample Book")
                .contains("원본 파일: book.epub")
                .contains("first")
                .contains("second")
                .contains("three")
                .doesNotContain("tsecond");
        assertThat(assembly.fullCoverage()).isTrue();
        assertThat(assembly.sourceChunkCount()).isEqualTo(3);
        assertThat(assembly.references()).singleElement().satisfies(reference -> assertThat(reference.metadata())
                .containsEntry("overviewCoverageStatus", "FULL")
                .containsEntry("chunkId", "doc-1:whole-document")
                .containsEntry("sourceRef", "chunks[0..2]"));
    }

    @Test
    void marksCoveragePartialWhenTheWholeDocumentExceedsTheLimit() {
        RagDocumentOverviewAssembler.Assembly assembly = assembler.assemble(List.of(
                result(0, 0, 200, "a".repeat(200)),
                result(1, 200, 400, "b".repeat(200))), 400, true);

        assertThat(assembly.fullCoverage()).isFalse();
        assertThat(assembly.context()).contains("chunk 0", "chunk 1");
        assertThat(assembly.context().length()).isLessThanOrEqualTo(400);
    }

    private RagSearchResult result(int order, int start, int end, String content) {
        return new RagSearchResult("doc-1", content, Map.of(
                "chunkOrder", order,
                "startOffset", start,
                "endOffset", end,
                "documentTitle", "Sample Book",
                "sourceFileName", "book.epub"), 1.0d);
    }
}

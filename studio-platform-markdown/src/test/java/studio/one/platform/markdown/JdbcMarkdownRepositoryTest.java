package studio.one.platform.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownExtractPart;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownPipelineExecutionStatus;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;
import studio.one.platform.markdown.infrastructure.JdbcMarkdownRepository;

class JdbcMarkdownRepositoryTest {
    private EmbeddedDatabase database;
    private JdbcMarkdownRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        try (Connection connection = database.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("schema/markdown/postgres/V1610__create_markdown_knowledge_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("schema/markdown/postgres/V1611__create_markdown_pipeline_execution.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("schema/markdown/postgres/V1612__create_markdown_extract_part.sql"));
        }
        repository = new JdbcMarkdownRepository(new NamedParameterJdbcTemplate(database));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void persistsRevisionAndOwnedLocatorResources() {
        Instant now = Instant.parse("2026-06-13T00:00:00Z");
        repository.saveDocument(new MarkdownDocument("mdoc-1", 10L, null, now, now));
        MarkdownRevision revision = new MarkdownRevision(
                "mrev-1", "mdoc-1", 10L, 20L, "conv-1", "PANDOC", "3.6",
                "{}", "options", "source", "content", "# Title", "sample.docx", "docx",
                "2001", "42", MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
        repository.saveRevision(revision);
        repository.replaceLocators("mrev-1", List.of(new MarkdownLocator(
                "mloc-1", "mrev-1", "SECTION", 1, "Title", 0, 7, null, "{}")));
        repository.replaceResources("mrev-1", List.of(new MarkdownResource(
                "mres-1", "mrev-1", "IMAGE", "image.png", 30L, "{}")));
        repository.replaceExtractParts("mrev-1", List.of(new MarkdownExtractPart(
                "mepart-1", "mrev-1", 1, 50, "COMPLETED", "pymupdf4llm",
                7, "# Title", null, null, 123L, "{}", now, now, now)));
        repository.saveDocument(new MarkdownDocument("mdoc-1", 10L, "mrev-1", now, now));
        repository.savePipelineExecution(new MarkdownPipelineExecution(
                "mrev-1", MarkdownPipelineExecutionStatus.FAILED, MarkdownPipelineStage.RAG_INDEX,
                MarkdownPipelineStage.CHUNKING, 1, "PIPELINE_FAILED", "failed", now, now, now));

        assertEquals("mrev-1", repository.findDocument("mdoc-1").orElseThrow().currentRevisionId());
        assertEquals("mrev-1", repository.findRevisionByConvertJobId("conv-1").orElseThrow().revisionId());
        assertEquals(1, repository.findLocators("mrev-1").size());
        assertEquals(1, repository.findResources("mrev-1").size());
        assertEquals(1, repository.findExtractParts("mrev-1").size());
        assertTrue(repository.findReusableRevision(10L, "source", "PANDOC", "3.6", "options").isPresent());
        assertEquals(MarkdownPipelineStage.RAG_INDEX,
                repository.findPipelineExecution("mrev-1").orElseThrow().currentStage());
    }
}

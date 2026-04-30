package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcVectorProjectionSqlTest {

    @Test
    void postgresJsonTextQuotesLiteralKey() {
        assertThat(JdbcVectorProjectionSql.jsonText("c", "chunkId", true))
                .isEqualTo("c.metadata ->> 'chunkId'");
    }

    @Test
    void postgresJsonTextKeepsNamedParameterKey() {
        assertThat(JdbcVectorProjectionSql.jsonText(null, ":filterKey0", true))
                .isEqualTo("metadata ->> :filterKey0");
    }

    @Test
    void mysqlJsonTextUsesJsonExtractPath() {
        assertThat(JdbcVectorProjectionSql.jsonText("c", "chunkId", false))
                .isEqualTo("JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.chunkId'))");
    }

    @Test
    void mysqlJsonTextUsesParameterizedPath() {
        assertThat(JdbcVectorProjectionSql.jsonText(null, ":filterKey0", false))
                .isEqualTo("JSON_UNQUOTE(JSON_EXTRACT(metadata, CONCAT('$.', :filterKey0)))");
    }

    @Test
    void orderByDisplayOrderClauseKeepsSpaceAfterOrderBy() {
        assertThat(JdbcVectorProjectionSql.orderByDisplayOrderClause(true))
                .isEqualTo(" ORDER BY p.display_order NULLS LAST, p.vector_item_id");
        assertThat("WHERE p.projection_id = :projectionId" + JdbcVectorProjectionSql.orderByDisplayOrderClause(true))
                .contains(" ORDER BY p.display_order");
    }
}

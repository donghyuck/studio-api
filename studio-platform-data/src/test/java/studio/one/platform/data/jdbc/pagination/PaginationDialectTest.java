package studio.one.platform.data.jdbc.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaginationDialectTest {

    @Test
    void mysqlDialectAppendsLimitOffsetClause() {
        PaginationDialect dialect = new MySqlPaginationDialect();

        assertThat(dialect.applyPagination("select * from sample", 5, 10))
                .isEqualTo("select * from sample LIMIT 5, 10");
    }

    @Test
    void mysqlDialectSupportsZeroOffset() {
        PaginationDialect dialect = new MySqlPaginationDialect();

        assertThat(dialect.applyPagination("select * from sample", 0, 10))
                .isEqualTo("select * from sample LIMIT 0, 10");
    }

    @Test
    void postgresDialectAppendsLimitThenOffset() {
        PaginationDialect dialect = new PostgresPaginationDialect();

        assertThat(dialect.applyPagination("select * from sample", 5, 10))
                .isEqualTo("select * from sample LIMIT 10 OFFSET 5");
    }

    @Test
    void oracleDialectWrapsQueryWithRownumBounds() {
        PaginationDialect dialect = new OraclePaginationDialect();

        assertThat(dialect.applyPagination("select * from sample order by id", 5, 10))
                .contains("ROWNUM <=")
                .contains("WHERE rnum > 5");
    }

    @Test
    void sqlServerDialectAddsFallbackOrderByWhenMissing() {
        PaginationDialect dialect = new SqlServerPaginationDialect();

        assertThat(dialect.applyPagination("select * from sample", 5, 10))
                .contains("ORDER BY (SELECT 1)")
                .endsWith("OFFSET 5 ROWS FETCH NEXT 10 ROWS ONLY");
    }

    @Test
    void defaultDialectReportsUnsupportedPagination() {
        PaginationDialect dialect = new DefaultPaginationDialect();

        assertThat(dialect.supportsPagination()).isFalse();
        assertThatThrownBy(() -> dialect.applyPagination("select 1", 0, 10))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Pagination is not supported");
    }
}

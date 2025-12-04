package studio.one.platform.data.jdbc.pagination;

public class SqlServerPaginationDialect implements PaginationDialect {

    @Override
    public String applyPagination(String sql, int offset, int limit) {
        // SQL Server 2012 이상 기준
        String trimmed = sql.trim();
        if (!trimmed.toLowerCase().contains("order by")) {
            // OFFSET/FETCH 는 ORDER BY 필수
            trimmed += " ORDER BY (SELECT 1)";
        }
        return trimmed + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
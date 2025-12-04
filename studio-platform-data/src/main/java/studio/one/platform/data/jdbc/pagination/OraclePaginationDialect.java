package studio.one.platform.data.jdbc.pagination;

public class OraclePaginationDialect implements PaginationDialect {

    @Override
    public String applyPagination(String sql, int offset, int limit) {
        int end = offset + limit;
        // 가장 단순한 ROWNUM 기반 페이징 (ORDER BY 있으면 서브쿼리 포함)
        return "SELECT * FROM (" +
                "  SELECT inner_.*, ROWNUM rnum FROM (" + sql + ") inner_ " +
                "  WHERE ROWNUM <= " + end +
                ") WHERE rnum > " + offset;
    }
}
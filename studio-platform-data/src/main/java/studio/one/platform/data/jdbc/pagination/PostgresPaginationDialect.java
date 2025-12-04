package studio.one.platform.data.jdbc.pagination;

public class PostgresPaginationDialect implements PaginationDialect {

    @Override
    public String applyPagination(String sql, int offset, int limit) {
        StringBuilder sb = new StringBuilder(sql.length() + 30);
        sb.append(sql);
        sb.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);
        return sb.toString();
    }
}
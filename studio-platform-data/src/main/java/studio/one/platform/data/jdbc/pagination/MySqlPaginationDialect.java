package studio.one.platform.data.jdbc.pagination;

public class MySqlPaginationDialect implements PaginationDialect {

    @Override
    public String applyPagination(String sql, int offset, int limit) {
        // offset, limit는 코드에서 만들어지는 int 값이므로 SQL Injection 리스크 없음
        StringBuilder sb = new StringBuilder(sql.length() + 30);
        sb.append(sql);
        sb.append(" LIMIT ").append(offset).append(", ").append(limit);
        return sb.toString();
    }
}
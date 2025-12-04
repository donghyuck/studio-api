package studio.one.platform.data.jdbc.pagination;

public class DefaultPaginationDialect implements PaginationDialect {

    @Override
    public String applyPagination(String sql, int offset, int limit) {
        throw new UnsupportedOperationException("Pagination is not supported for this database");
    }

    @Override
    public boolean supportsPagination() {
        return false;
    }
}

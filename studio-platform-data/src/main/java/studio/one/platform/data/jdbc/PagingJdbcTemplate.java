package studio.one.platform.data.jdbc;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.data.jdbc.pagination.PaginationDialect;
import studio.one.platform.data.jdbc.pagination.PaginationDialectResolver;

/**
 * Spring JdbcTemplate 위에 "페이지 단위 조회" 기능만 깔끔하게 얹은 버전.
 * 기존 scrollable ResultSet 기반 구현을 대체하는 용도.
 */
@Slf4j
public class PagingJdbcTemplate extends JdbcTemplate {

    private final PaginationDialect paginationDialect;

    public PagingJdbcTemplate(DataSource dataSource) {
        super(dataSource);
        this.paginationDialect = new PaginationDialectResolver().resolve(dataSource);
        log.info("PagingJdbcTemplate initialized with dialect: {}", paginationDialect.getClass().getSimpleName());
    }

    public PagingJdbcTemplate(DataSource dataSource, boolean lazyInit) {
        super(dataSource, lazyInit);
        this.paginationDialect = new PaginationDialectResolver().resolve(dataSource);
        log.info("PagingJdbcTemplate initialized with dialect: {}", paginationDialect.getClass().getSimpleName());
    }

    // 필요하면 @Autowired DataSource + @PostConstruct 로도 생성 가능

    // ---------------------------
    // Public paging query methods
    // ---------------------------

    public List<Map<String, Object>> queryPage(String sql, int startIndex, int numResults)
            throws DataAccessException {
        String paginatedSql = paginationDialect.applyPagination(sql, startIndex, numResults);
        if (log.isDebugEnabled()) {
            log.debug("Executing paginated query: {}", paginatedSql.replaceAll("[\r\n]", " "));
        }
        return super.queryForList(paginatedSql);
    }

    public List<Map<String, Object>> queryPage(String sql, int startIndex, int numResults, Object... args)
            throws DataAccessException {
        String paginatedSql = paginationDialect.applyPagination(sql, startIndex, numResults);
        if (log.isDebugEnabled()) {
            log.debug("Executing paginated query: {}", paginatedSql.replaceAll("[\r\n]", " "));
        }
        return super.queryForList(paginatedSql, args);
    }

    public <T> List<T> queryPage(String sql, int startIndex, int numResults, Class<T> elementType, Object... args)
            throws DataAccessException {
        String paginatedSql = paginationDialect.applyPagination(sql, startIndex, numResults);
        if (log.isDebugEnabled()) {
            log.debug("Executing paginated query: {}", paginatedSql.replaceAll("[\r\n]", " "));
        }
        return super.query(paginatedSql, getSingleColumnRowMapper(elementType), args);
    }

    public <T> List<T> queryPage(String sql, int startIndex, int numResults, RowMapper<T> rowMapper, Object... args)
            throws DataAccessException {
        String paginatedSql = paginationDialect.applyPagination(sql, startIndex, numResults);
        if (log.isDebugEnabled()) {
            log.debug("Executing paginated query: {}", paginatedSql.replaceAll("[\r\n]", " "));
        }
        return super.query(paginatedSql, rowMapper, args);
    }
}
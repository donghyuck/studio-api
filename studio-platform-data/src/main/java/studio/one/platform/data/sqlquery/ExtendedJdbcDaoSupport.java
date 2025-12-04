package studio.one.platform.data.sqlquery;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.lang.Nullable;

import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.data.sqlquery.mapping.BoundSql;
import studio.one.platform.data.sqlquery.mapping.MapperSource;
import studio.one.platform.data.jdbc.PagingJdbcTemplate;

public abstract class ExtendedJdbcDaoSupport extends JdbcDaoSupport {

    @Nullable
    private SqlHelper sqlHelper;

    @Nullable
    private LobHandler lobHandler = new DefaultLobHandler();


    @Autowired(required = false)
    public void setSqlQueryFactory(@Qualifier(SqlQueryFactory.SERVICE_NAME) SqlQueryFactory sqlQueryFactory) {
        this.sqlHelper = SqlHelper.Builder.build(sqlQueryFactory);
    }

    @Override
    protected JdbcTemplate createJdbcTemplate(DataSource dataSource) {
        return new PagingJdbcTemplate(dataSource);
    }

    public LobHandler getLobHandler() {
        return lobHandler;
    }

    public void setLobHandler(LobHandler lobHandler) {
        this.lobHandler = lobHandler;
    }

    public BoundSql getBoundSql(String statement) {
        assertSqlHelperInitialized();
        return sqlHelper.getBoundSql(statement);
    }

    public BoundSql getBoundSql(String statement, Object... params) {
        assertSqlHelperInitialized();
        return sqlHelper.getBoundSql(statement, params);
    }

    public BoundSql getBoundSqlWithAdditionalParameter(String statement, Object additionalParameter) {
        assertSqlHelperInitialized();
        return sqlHelper.getBoundSqlWithAdditionalParameter(statement, additionalParameter);
    }

    public BoundSql getBoundSqlWithAdditionalParameter(String statement, Object parameters, Object additionalParameter) {
        assertSqlHelperInitialized();
        return sqlHelper.getBoundSql(statement, parameters, additionalParameter);
    }

    public MapperSource getMapperSource(String name) {
        assertSqlHelperInitialized();
        return sqlHelper.getMapperSource(name);
    }

    public PagingJdbcTemplate getPagingJdbcTemplate() {
        JdbcTemplate jdbcTemplate = getJdbcTemplate();
        if (!(jdbcTemplate instanceof PagingJdbcTemplate)) {
            throw new IllegalStateException("JdbcTemplate is not an instance of ExtendedJdbcTemplate.");
        }
        return (PagingJdbcTemplate) jdbcTemplate;
    }

    private void assertSqlHelperInitialized() {
        if (this.sqlHelper == null) {
            throw new IllegalStateException("SqlHelper is not initialized. Use constructor or setter injection.");
        }
    }
}

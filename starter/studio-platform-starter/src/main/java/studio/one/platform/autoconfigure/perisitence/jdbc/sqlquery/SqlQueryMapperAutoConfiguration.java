package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jdbc.sqlquery.SqlQueryMapperAutoConfiguration} instead.
 */
@Configuration
@EnableSqlMappers
@ConditionalOnClass(SqlQueryFactory.class)
@ConditionalOnBean({ DataSource.class, SqlQueryFactory.class })
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX + ".sql-query", name = "enabled", havingValue = "true", matchIfMissing = true)
@Deprecated(forRemoval = false)
public class SqlQueryMapperAutoConfiguration
        extends studio.one.platform.autoconfigure.persistence.jdbc.sqlquery.SqlQueryMapperAutoConfiguration {
}

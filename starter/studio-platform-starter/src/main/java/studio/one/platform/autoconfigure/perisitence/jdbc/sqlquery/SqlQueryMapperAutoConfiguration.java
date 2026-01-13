package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studio.one.platform.autoconfigure.JdbcProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;

@Configuration
@EnableSqlMappers
@ConditionalOnClass(SqlQueryFactory.class)
@ConditionalOnBean({ DataSource.class, SqlQueryFactory.class })
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX + ".sql-query", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SqlQueryMapperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlStatementBeanPostProcessor sqlStatementBeanPostProcessor(JdbcProperties jdbcProperties) {
        SqlStatementBeanPostProcessor processor = new SqlStatementBeanPostProcessor();
        processor.setFailFast(jdbcProperties.getSql().isFailFast());
        return processor;
    }
}

package studio.one.platform.autoconfigure.perisitence.jdbc;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import studio.one.platform.autoconfigure.JdbcProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jdbc.JdbcAutoConfiguration} instead.
 */
@Configuration
@ConditionalOnClass({ javax.sql.DataSource.class, JdbcTemplate.class })
@ConditionalOnBean(javax.sql.DataSource.class)
@EnableConfigurationProperties(JdbcProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Deprecated(forRemoval = false)
public class JdbcAutoConfiguration extends studio.one.platform.autoconfigure.persistence.jdbc.JdbcAutoConfiguration {

    public JdbcAutoConfiguration(ObjectProvider<I18n> i18nProvider) {
        super(i18nProvider);
    }
}

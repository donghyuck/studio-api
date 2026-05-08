package studio.one.platform.autoconfigure.persistence.jdbc;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.JdbcProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration
@RequiredArgsConstructor
@ConditionalOnClass({ DataSource.class, JdbcTemplate.class })
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(JdbcProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class JdbcAutoConfiguration {

    protected static final String FEATURE_NAME = "JDBC";
    private final ObjectProvider<I18n> i18nProvider;

    @Bean(ServiceNames.JDBC_TEMPLATE)
    @ConditionalOnMissingBean(name = ServiceNames.JDBC_TEMPLATE)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(JdbcTemplate.class, true), LogUtils.red(State.CREATED.toString())));
        return new JdbcTemplate(dataSource, true);
    }

    @Bean(ServiceNames.NAMED_JDBC_TEMPLATE)
    @ConditionalOnMissingBean(name = ServiceNames.NAMED_JDBC_TEMPLATE)
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(NamedParameterJdbcTemplate.class, true), LogUtils.red(State.CREATED.toString())));
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

}

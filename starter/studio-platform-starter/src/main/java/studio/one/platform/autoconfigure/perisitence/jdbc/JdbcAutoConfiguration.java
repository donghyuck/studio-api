package studio.one.platform.autoconfigure.perisitence.jdbc;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

@Configuration
@RequiredArgsConstructor 
@ConditionalOnClass({ DataSource.class, JdbcTemplate.class })
@ConditionalOnBean(DataSource.class)
@Slf4j
public class JdbcAutoConfiguration {

    private final I18n i18n;

    @Bean(ServiceNames.JDBC_TEMPLATE)
    @ConditionalOnMissingBean(name= ServiceNames.JDBC_TEMPLATE)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, "jdbc",
                LogUtils.blue(JdbcTemplate.class, true), LogUtils.red(State.CREATED.toString())));
        return new JdbcTemplate(dataSource, true);
    }

    @Bean(ServiceNames.NAMED_JDBC_TEMPLATE)
    @ConditionalOnMissingBean(name=ServiceNames.NAMED_JDBC_TEMPLATE)
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            @Qualifier(ServiceNames.JDBC_TEMPLATE) JdbcTemplate jdbcTemplate) {
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, "jdbc",
                LogUtils.blue(NamedParameterJdbcTemplate.class, true), LogUtils.red(State.CREATED.toString())));
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

}

package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.adapters.vector.PgVectorStoreAdapter;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class VectorStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStorePort.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public VectorStorePort vectorStorePort(JdbcTemplate jdbcTemplate, ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(VectorStorePort.class, true),
                LogUtils.green(PgVectorStoreAdapter.class, true),
                LogUtils.red(State.CREATED.toString())));

        return new PgVectorStoreAdapter(jdbcTemplate);
    }
}

package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.adapters.vector.PgVectorJdbcMapper;
import studio.one.platform.ai.adapters.vector.PgVectorStoreAdapterV2;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VectorStoreProperties.class)
@Slf4j
public class VectorStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(value = VectorStorePort.class, type = "org.mybatis.spring.SqlSessionTemplate")
    @ConditionalOnBean(JdbcTemplate.class)
    public VectorStorePort jdbcVectorStorePort(JdbcTemplate jdbcTemplate, ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                AiProviderRegistryConfiguration.FEATURE_NAME,
                LogUtils.blue(VectorStorePort.class, true),
                LogUtils.green(PgVectorStoreAdapterV2.class, true),
                LogUtils.red(State.CREATED.toString())));

        return new PgVectorStoreAdapterV2(jdbcTemplate);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SqlSessionTemplate.class)
    @ConditionalOnBean(SqlSessionTemplate.class)
    @Slf4j
    static class PgVectorMyBatisConfiguration {

        @Bean
        @ConditionalOnMissingBean({ PgVectorMapper.class, VectorStorePort.class })
        @ConditionalOnBean(JdbcTemplate.class)
        public PgVectorMapper pgVectorMapper(SqlSessionTemplate sqlSessionTemplate, JdbcTemplate jdbcTemplate) {
            if (!hasMappedStatements(sqlSessionTemplate)) {
                log.info("PgVector MyBatis mapper XML is not loaded. Falling back to SQLQuery-free JDBC mapper.");
                return new PgVectorJdbcMapper(jdbcTemplate);
            }
            return sqlSessionTemplate.getMapper(PgVectorMapper.class);
        }

        private boolean hasMappedStatements(SqlSessionTemplate sqlSessionTemplate) {
            return hasMappedStatement(sqlSessionTemplate, "upsertChunk")
                    && hasMappedStatement(sqlSessionTemplate, "search")
                    && hasMappedStatement(sqlSessionTemplate, "deleteByObject")
                    && hasMappedStatement(sqlSessionTemplate, "searchByObject")
                    && hasMappedStatement(sqlSessionTemplate, "hybridSearch")
                    && hasMappedStatement(sqlSessionTemplate, "hybridSearchByObject")
                    && hasMappedStatement(sqlSessionTemplate, "exists")
                    && hasMappedStatement(sqlSessionTemplate, "listByObject")
                    && hasMappedStatement(sqlSessionTemplate, "listByObjectPage")
                    && hasMappedStatement(sqlSessionTemplate, "metadataByObject");
        }

        private boolean hasMappedStatement(SqlSessionTemplate sqlSessionTemplate, String statementId) {
            return sqlSessionTemplate.getConfiguration()
                    .hasStatement(PgVectorMapper.class.getName() + "." + statementId, false);
        }

        @Bean
        @ConditionalOnMissingBean(VectorStorePort.class)
        @ConditionalOnBean(JdbcTemplate.class)
        public VectorStorePort vectorStorePort(
                PgVectorMapper mapper,
                JdbcTemplate jdbcTemplate,
                ObjectProvider<I18n> i18nProvider) {

            I18n i18n = I18nUtils.resolve(i18nProvider);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DEPENDS_ON,
                    AiProviderRegistryConfiguration.FEATURE_NAME,
                    LogUtils.blue(VectorStorePort.class, true),
                    LogUtils.green(PgVectorStoreAdapterV2.class, true),
                    LogUtils.red(State.CREATED.toString())));

            return new PgVectorStoreAdapterV2(mapper, jdbcTemplate.getDataSource());
        }
    }
}

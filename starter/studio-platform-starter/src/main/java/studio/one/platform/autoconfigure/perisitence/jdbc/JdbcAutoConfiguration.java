package studio.one.platform.autoconfigure.perisitence.jdbc;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.JdbcProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.data.sqlquery.builder.xml.XmlSqlSetBuilder;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactoryBuilder;
import studio.one.platform.data.sqlquery.factory.impl.SqlQueryFactoryImpl;
import studio.one.platform.data.sqlquery.mapping.MappedStatement;
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
 
    @Bean(ServiceNames.SQL_QUERY_CONFIGURATION)
    @ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX
            + ".sql-query", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "freemarker.template.Configuration")
    studio.one.platform.data.sqlquery.factory.Configuration sqlQueryConfiguration() {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(studio.one.platform.data.sqlquery.factory.Configuration.class, true),
                LogUtils.red(State.CREATED.toString())));
        return new studio.one.platform.data.sqlquery.factory.Configuration(i18nProvider);
    }
 
    @Bean(ServiceNames.SQL_QUERY_FACTORY)
    @ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jdbc.PREFIX
            + ".sql-query", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "freemarker.template.Configuration")
    SqlQueryFactory sqlQueryFactory(
        JdbcProperties jdbcProperties,
        @Qualifier(ServiceNames.SQL_QUERY_CONFIGURATION) studio.one.platform.data.sqlquery.factory.Configuration sqlQueryConfiguration) {
        SqlQueryFactory impl =  SqlQueryFactoryBuilder.build(sqlQueryConfiguration);
        
        I18n i18n = I18nUtils.resolve(i18nProvider);

        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(SqlQueryFactoryImpl.class, true),
                LogUtils.red(State.CREATED.toString())));
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(SqlQueryFactoryImpl.class, true),
                LogUtils.red(State.INITIALIZING.toString()))); 
        List<String> locs = jdbcProperties.getSql().getLocations();
        boolean usingDefault = (locs == null || locs.isEmpty()); 
        List<String> resolvedLocations = usingDefault
                ? List.of("classpath*:sql/*-sqlset.xml")
                : locs; 
        if (usingDefault) {
            log.info("[SqlQueryFactory] No SQL locations configured. Using default: classpath*:sql/*-sqlset.xml");
        } else {
            log.info("[SqlQueryFactory] SQL locations configured: {}", resolvedLocations);
        }
        loadSql(impl, resolvedLocations);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(SqlQueryFactoryImpl.class, true),
                LogUtils.red(State.INITIALIZED.toString())));
        return impl;
    }

    private void loadSql(SqlQueryFactory impl, List<String> resolvedLocations) {
        studio.one.platform.data.sqlquery.factory.Configuration config = impl.getConfiguration();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String locationPattern : resolvedLocations) {
            try {
                Resource[] resources = resolver.getResources(locationPattern);
                if (resources.length == 0) {
                    log.warn("[SqlQueryFactory] No SQL resource matched pattern: {}", locationPattern);
                    continue;
                }
                for (Resource resource : resources) {
                    String key = resource.getURI().toString();
                    boolean loaded = config.isResourceLoaded(key);
                    log.info("[{}] loading SQL set (loaded={}, uri={})",
                            impl.getClass().getSimpleName(),
                            loaded,
                            key);
                    if (!loaded) {
                        XmlSqlSetBuilder builder = new XmlSqlSetBuilder(
                                resource.getInputStream(),
                                config,
                                key,
                                null);
                        try {
                            builder.parse();
                        } catch (Exception ex) {
                            log.error("[SqlQueryFactory] XML parsing error: {}", key, ex);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("[SqlQueryFactory] Resource resolution error for pattern: {}", locationPattern, e);
            }
        }
        List<String> queryKeys = config.getMappedStatements().stream()
                .map(MappedStatement::getId)
                .sorted()
                .collect(Collectors.toList());
        if (queryKeys.isEmpty()) {
            log.warn("[SqlQueryFactory] No SQL query keys registered.");
        } else {
            log.info("[SqlQueryFactory] Registered SQL query keys ({}): {}", queryKeys.size(), queryKeys);
        }
    }

}

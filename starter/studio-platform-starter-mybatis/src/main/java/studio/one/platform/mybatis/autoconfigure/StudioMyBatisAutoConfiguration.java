package studio.one.platform.mybatis.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import studio.one.platform.data.mybatis.StudioMyBatisDatabaseIdProviderFactory;
import studio.one.platform.data.mybatis.StudioMyBatisProperties;

@AutoConfiguration(before = MybatisAutoConfiguration.class)
@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })
@EnableConfigurationProperties(StudioMyBatisProperties.class)
public class StudioMyBatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DatabaseIdProvider studioMyBatisDatabaseIdProvider(StudioMyBatisProperties properties) {
        return StudioMyBatisDatabaseIdProviderFactory.create(properties.getDatabaseIdAliases());
    }

    @Bean
    @ConditionalOnMissingBean(name = "studioMyBatisConfigurationCustomizer")
    ConfigurationCustomizer studioMyBatisConfigurationCustomizer(StudioMyBatisProperties properties,
            Environment environment) {
        return configuration -> {
            if (!hasProperty(environment, "mybatis.configuration.map-underscore-to-camel-case")) {
                configuration.setMapUnderscoreToCamelCase(properties.isMapUnderscoreToCamelCase());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "studioMyBatisSqlSessionFactoryBeanCustomizer")
    SqlSessionFactoryBeanCustomizer studioMyBatisSqlSessionFactoryBeanCustomizer(
            StudioMyBatisProperties properties,
            Environment environment,
            ResourceLoader resourceLoader) {
        return factory -> {
            if (!hasProperty(environment, "mybatis.mapper-locations")) {
                Resource[] mapperLocations = resolveMapperLocations(properties, resourceLoader);
                if (mapperLocations.length > 0) {
                    factory.setMapperLocations(mapperLocations);
                }
            }
            if (!hasProperty(environment, "mybatis.type-aliases-package")
                    && StringUtils.hasText(properties.getTypeAliasesPackage())) {
                factory.setTypeAliasesPackage(properties.getTypeAliasesPackage());
            }
            if (!hasProperty(environment, "mybatis.type-handlers-package")
                    && StringUtils.hasText(properties.getTypeHandlersPackage())) {
                factory.setTypeHandlersPackage(properties.getTypeHandlersPackage());
            }
        };
    }

    private static Resource[] resolveMapperLocations(StudioMyBatisProperties properties, ResourceLoader resourceLoader) {
        ResourcePatternResolver resolver = ResourcePatternResolver.class.isInstance(resourceLoader)
                ? ResourcePatternResolver.class.cast(resourceLoader)
                : new PathMatchingResourcePatternResolver(resourceLoader);
        List<Resource> resources = new ArrayList<>();
        List<String> mapperLocations = properties.getMapperLocations() == null ? List.of() : properties.getMapperLocations();
        for (String location : mapperLocations) {
            if (!StringUtils.hasText(location)) {
                continue;
            }
            try {
                for (Resource resource : resolver.getResources(location)) {
                    resources.add(resource);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to resolve MyBatis mapper location: " + location, ex);
            }
        }
        return resources.toArray(Resource[]::new);
    }

    private static boolean hasProperty(Environment environment, String name) {
        Binder binder = Binder.get(environment);
        return binder.bind(name, String.class).map(StringUtils::hasText).orElse(false)
                || binder.bind(name, Bindable.listOf(String.class)).isBound();
    }
}

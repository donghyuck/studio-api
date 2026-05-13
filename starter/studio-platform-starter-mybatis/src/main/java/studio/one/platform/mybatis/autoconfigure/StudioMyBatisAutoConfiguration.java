package studio.one.platform.mybatis.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
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

@Configuration
@AutoConfigureBefore(MybatisAutoConfiguration.class)
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
            Resource[] mapperLocations = resolveMapperLocations(properties, environment, resourceLoader);
            if (mapperLocations.length > 0) {
                factory.setMapperLocations(mapperLocations);
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

    private static Resource[] resolveMapperLocations(StudioMyBatisProperties properties, Environment environment,
            ResourceLoader resourceLoader) {
        ResourcePatternResolver resolver = ResourcePatternResolver.class.isInstance(resourceLoader)
                ? ResourcePatternResolver.class.cast(resourceLoader)
                : new PathMatchingResourcePatternResolver(resourceLoader);
        Map<String, Resource> resources = new LinkedHashMap<>();
        List<String> standardMapperLocations = standardMapperLocations(environment);
        for (String location : standardMapperLocations) {
            addResources(resolver, resources, location);
        }
        List<String> mapperLocations = properties.getMapperLocations() == null ? List.of() : properties.getMapperLocations();
        for (String location : mapperLocations) {
            addResources(resolver, resources, location);
        }
        return resources.values().toArray(Resource[]::new);
    }

    private static List<String> standardMapperLocations(Environment environment) {
        Binder binder = Binder.get(environment);
        List<String> locations = new ArrayList<>();
        binder.bind("mybatis.mapper-locations", Bindable.listOf(String.class)).ifBound(locations::addAll);
        binder.bind("mybatis.mapper-locations", String.class)
                .ifBound(value -> {
                    if (StringUtils.hasText(value)) {
                        locations.addAll(StringUtils.commaDelimitedListToSet(value));
                    }
                });
        return locations;
    }

    private static void addResources(ResourcePatternResolver resolver, Map<String, Resource> resources, String location) {
        if (!StringUtils.hasText(location)) {
            return;
        }
        try {
            for (Resource resource : resolver.getResources(location)) {
                resources.putIfAbsent(resourceKey(resource), resource);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resolve MyBatis mapper location: " + location, ex);
        }
    }

    private static String resourceKey(Resource resource) throws IOException {
        try {
            return resource.getURL().toExternalForm();
        } catch (IOException ex) {
            return resource.getDescription();
        }
    }

    private static boolean hasProperty(Environment environment, String name) {
        Binder binder = Binder.get(environment);
        return binder.bind(name, String.class).map(StringUtils::hasText).orElse(false)
                || binder.bind(name, Bindable.listOf(String.class)).isBound();
    }
}

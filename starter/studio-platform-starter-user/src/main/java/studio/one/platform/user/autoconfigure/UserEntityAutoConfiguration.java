package studio.one.platform.user.autoconfigure;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.event.listener.UserCacheEvictListener;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.user.autoconfigure.condition.ConditionalOnUserPersistence;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@EnableConfigurationProperties({ PersistenceProperties.class, UserFeatureProperties.class })
@ConditionalOnExpression("${" + PropertyKeys.Features.User.ENABLED + ":true} && ${" + PropertyKeys.Features.User.USE_DEFAULT + ":true}")
@Slf4j
@RequiredArgsConstructor
public class UserEntityAutoConfiguration {
        
        protected static final String FEATURE_NAME = "User";
        private final ObjectProvider<I18n> i18nProvider;

        @Bean
        static BeanDefinitionRegistryPostProcessor userRepositoryExcluder(UserFeatureProperties props) {
                List<Pattern> patterns = buildPatterns(props.getExcludeRepositoryPackages(), props.getExcludeJdbcRepositoryPackages());
                return new ExcludePostProcessor(patterns);
        }

        private static List<Pattern> buildPatterns(List<String>... lists) {
                List<Pattern> out = new java.util.ArrayList<>();
                if (lists != null) {
                        for (List<String> l : lists) {
                                if (l == null)
                                        continue;
                                l.stream()
                                                .filter(s -> s != null && !s.isBlank())
                                                .forEach(s -> {
                                                        try {
                                                                out.add(Pattern.compile(s.trim()));
                                                        } catch (Exception ignored) {
                                                                // 무효 regex는 건너뜀
                                                        }
                                                });
                        }
                }
                return out;
        }

        private static boolean matches(String className, List<Pattern> patterns) {
                for (Pattern p : patterns) {
                        if (p.matcher(className).find()) {
                                return true;
                        }
                }
                return false;
        }

        private static class ExcludePostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
                private final List<Pattern> patterns;

                ExcludePostProcessor(List<Pattern> patterns) {
                        this.patterns = patterns;
                }

                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                        if (patterns == null || patterns.isEmpty()) {
                                return;
                        }
                        for (String name : registry.getBeanDefinitionNames()) {
                                String className = registry.getBeanDefinition(name).getBeanClassName();
                                if (className != null && matches(className, patterns)) {
                                        registry.removeBeanDefinition(name);
                                }
                        }
                }

                @Override
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                        // no-op
                }

                @Override
                public int getOrder() {
                        return Ordered.LOWEST_PRECEDENCE;
                }
        }

        /**
         * JPA Entity 스캔.
         */
        @Configuration
        @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
        @ConditionalOnUserPersistence(PersistenceProperties.Type.jpa)
        @SuppressWarnings("java:S1118")
        static class EntityScanConfig {
                @Bean
                static BeanDefinitionRegistryPostProcessor userEntityScanRegistrar() {
                        return EntityScanRegistrarSupport.entityScanRegistrar(
                                        PropertyKeys.Features.User.PREFIX + ".entity-packages",
                                        new String[] { UserFeatureProperties.DEFAULT_ENTITY_PACKAGE },
                                        PropertyKeys.Features.User.PREFIX + ".exclude-entity-packages");
                }
        }

        /**
         * JPA Repository 스캔.
         */
        @Configuration(proxyBeanMethods = false)
        @AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
        @ConditionalOnBean(EntityManagerFactory.class)
        @ConditionalOnUserPersistence(PersistenceProperties.Type.jpa)
        @EnableJpaRepositories(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".repository-packages:" + UserFeatureProperties.DEFAULT_REPOSITORY_PACKAGE + "}")
        static class JpaWiring {
        }

        /**
         * JDBC 스캔
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnUserPersistence(PersistenceProperties.Type.jdbc)
        @ComponentScan(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".jdbc-repository-packages:" + UserFeatureProperties.JDBC_REPOSITORY_PACKAGE + "}")
        static class JdbcWiring {

        }

        /**
         * Service Impl 스캔
         */
        @Configuration(proxyBeanMethods = false)
        @ComponentScan(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".component-packages:"  + UserFeatureProperties.DEFAULT_COMPONENT_PACKAGE + "}")
        static class UserComponentScan {

        }

        @Bean
        @ConditionalOnMissingBean
        public Clock jwtClock() {
                return Clock.systemUTC();
        }

        @Bean
        @ConditionalOnMissingBean(UserCacheEvictListener.class)
        @ConditionalOnBean(CacheManager.class)
        UserCacheEvictListener userCacheEvictListener(CacheManager cacheManager) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
                log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                                LogUtils.blue(UserCacheEvictListener.class, true),
                                LogUtils.red(State.CREATED.toString())));
                return new UserCacheEvictListener(cacheManager);
        }
}

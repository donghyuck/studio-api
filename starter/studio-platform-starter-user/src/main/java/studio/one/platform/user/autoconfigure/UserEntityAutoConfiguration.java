package studio.one.platform.user.autoconfigure;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.i18n.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.LogUtils;

@AutoConfiguration
@ConditionalOnClass(EnableJpaRepositories.class)
@EnableConfigurationProperties({ UserFeatureProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.User.PREFIX, name = "enabled", havingValue = "true")
@Slf4j
@SuppressWarnings("java:S1118")
public class UserEntityAutoConfiguration {
 
    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class) 
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {

        @Bean
        static BeanDefinitionRegistryPostProcessor userEntityScanRegistrar(Environment env, I18n i18n) { 
             
            final String propKey = PropertyKeys.Features.User.PREFIX + ".entity-packages";
            final String defaultPkg = UserFeatureProperties.DEFAULT_ENTITY_PACKAGE;

            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.PREPARING, "User",  propKey, defaultPkg));
            String configured = env.getProperty(propKey);
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.CONFIG, "User",  propKey, configured )); 
            log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EntityScan.FINISH, "User")); 
            return EntityScanRegistrarSupport.entityScanRegistrar(
                    PropertyKeys.Features.User.PREFIX + ".entity-packages",
                    UserFeatureProperties.DEFAULT_ENTITY_PACKAGE);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @EnableJpaRepositories(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".repository-packages:" + UserFeatureProperties.DEFAULT_REPOSITORY_PACKAGE + "}")
    @ComponentScan(basePackages = "${" + PropertyKeys.Features.User.PREFIX + ".component-packages:" + UserFeatureProperties.DEFAULT_COMPONENT_PACKAGE + "}")
    static class JpaWiring {
    }

}
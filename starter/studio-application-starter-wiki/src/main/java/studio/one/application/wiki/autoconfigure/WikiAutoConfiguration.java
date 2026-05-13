package studio.one.application.wiki.autoconfigure;

import javax.persistence.EntityManagerFactory;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageEntity;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageJpaRepository;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageRevisionJpaRepository;
import studio.one.application.wiki.application.usecase.WikiPageService;
import studio.one.application.wiki.application.usecase.WikiRenderService;
import studio.one.application.wiki.application.service.DefaultWikiPageService;
import studio.one.application.wiki.application.service.DefaultWikiRenderService;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

@Configuration
@AutoConfigureAfter(name = "studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration")
@EnableConfigurationProperties(WikiFeatureProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".wiki", name = "enabled", havingValue = "true")
public class WikiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WikiRenderService wikiRenderService() {
        return new DefaultWikiRenderService();
    }

    @Bean
    @ConditionalOnBean({
            WikiPageJpaRepository.class,
            WikiPageRevisionJpaRepository.class,
            WorkspacePermissionService.class })
    @ConditionalOnMissingBean
    WikiPageService wikiPageService(
            WikiPageJpaRepository pageRepository,
            WikiPageRevisionJpaRepository revisionRepository,
            WorkspacePermissionService permissionService,
            WikiRenderService renderService) {
        return new DefaultWikiPageService(
                pageRepository,
                revisionRepository,
                permissionService,
                renderService);
    }

    @Configuration
    @AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".wiki", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @SuppressWarnings("java:S1118")
    static class EntityScanConfig {
        @Bean
        static BeanDefinitionRegistryPostProcessor wikiEntityScanRegistrar(Environment env) {
            String entityKey = PropertyKeys.Features.PREFIX + ".wiki.entity-packages";
            return EntityScanRegistrarSupport.entityScanRegistrar(entityKey, WikiPageEntity.class.getPackageName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureAfter(EntityScanConfig.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".wiki", name = "persistence", havingValue = "jpa", matchIfMissing = true)
    @EnableJpaRepositories(basePackageClasses = {
            WikiPageJpaRepository.class,
            WikiPageRevisionJpaRepository.class })
    static class WikiJpaConfig {
    }
}

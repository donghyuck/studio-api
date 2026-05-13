/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file TemplateAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.application.template.autoconfigure;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfig;
import org.springframework.web.context.WebApplicationContext;

import studio.one.application.template.infrastructure.persistence.jpa.TemplateEntity;
import studio.one.application.template.infrastructure.persistence.jdbc.TemplateJdbcRepository;
import studio.one.application.template.infrastructure.persistence.jpa.TemplateJpaPersistenceRepository;
import studio.one.application.template.infrastructure.persistence.jpa.TemplateJpaRepository;
import studio.one.application.template.application.service.FreemarkerTemplateBuilder;
import studio.one.application.template.application.usecase.TemplatesService;
import studio.one.application.template.application.service.TemplatesServiceImpl;
import studio.one.application.template.domain.port.TemplatePersistenceRepository;
import studio.one.application.template.web.controller.TemplateMgmtController;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.jdbc.JdbcDatabaseSupport;
import studio.one.platform.constant.PropertyKeys;
import studio.one.application.template.autoconfigure.condition.ConditionalOnTemplatePersistence;

/**
 * Auto-configuration for template service (JPA/JDBC + REST controller).
 *
 * @author  donghyuck, son
 * @since 2025-12-09
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-09  donghyuck, son: 최초 생성.
 * </pre>
 */

@Configuration
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".template", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({ TemplateFeatureProperties.class, PersistenceProperties.class })
public class TemplateAutoConfiguration {

    @Configuration
    @ConditionalOnClass(EntityManagerFactory.class)
    @ConditionalOnBean(name = "entityManagerFactory")
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = TemplateJpaRepository.class)
    @EntityScan(basePackageClasses = TemplateEntity.class)
    static class TemplateJpaConfig {
    }

    @Bean
    @ConditionalOnMissingBean(TemplatePersistenceRepository.class)
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jpa)
    public TemplatePersistenceRepository templateJpaPersistenceRepository(
            TemplateJpaRepository templateRepository) {
        return new TemplateJpaPersistenceRepository(templateRepository);
    }

    @Bean
    @ConditionalOnMissingBean(TemplatePersistenceRepository.class)
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jdbc)
    public TemplatePersistenceRepository templateJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        JdbcDatabaseSupport.requirePostgreSQL(jdbcTemplate, "template");
        return new TemplateJdbcRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TemplatesService.class)
    public TemplatesServiceImpl templatesService(
            TemplateFeatureProperties templateFeatureProperties,
            PersistenceProperties persistenceProperties,
            ObjectProvider<TemplatePersistenceRepository> repositoryProvider,
            FreemarkerTemplateBuilder templateBuilder) {

        PersistenceProperties.Type type = resolveTemplatePersistence(templateFeatureProperties, persistenceProperties);
        TemplatePersistenceRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("Persistence repository is not available for template service: " + type);
        }
        if (type == PersistenceProperties.Type.jpa && repository instanceof TemplateJdbcRepository) {
            throw new IllegalStateException("JPA persistence selected but TemplateJpaPersistenceRepository is not available");
        }
        if (type == PersistenceProperties.Type.jdbc && repository instanceof TemplateJpaPersistenceRepository) {
            throw new IllegalStateException("JDBC persistence selected but TemplateJdbcRepository is not available");
        }
        return new TemplatesServiceImpl(repository, templateBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(FreemarkerTemplateBuilder.class)
    public FreemarkerTemplateBuilder freemarkerTemplateBuilder(
            ObjectProvider<FreeMarkerConfig> freeMarkerConfigProvider,
            ObjectProvider<freemarker.template.Configuration> configurationProvider,
            ObjectProvider<WebApplicationContext> webContextProvider) {
        WebApplicationContext context = webContextProvider.getIfAvailable();
        javax.servlet.ServletContext servletContext = (context != null) ? context.getServletContext() : null;
        FreeMarkerConfig freeMarkerConfig = freeMarkerConfigProvider.getIfAvailable();
        if (freeMarkerConfig != null) {
            return new FreemarkerTemplateBuilder(servletContext, freeMarkerConfig);
        }
        freemarker.template.Configuration configuration = configurationProvider.getIfAvailable();
        if (configuration != null) {
            return new FreemarkerTemplateBuilder(servletContext, new FreeMarkerConfig() {
                @Override
                public freemarker.template.Configuration getConfiguration() {
                    return configuration;
                }

                @Override
                public freemarker.ext.jsp.TaglibFactory getTaglibFactory() {
                    return null;
                }
            });
        }
        return new FreemarkerTemplateBuilder(servletContext, null);
    }

    @Configuration
    @ConditionalOnClass(name = {
            "javax.validation.Valid",
            "org.springframework.data.domain.Pageable",
            "org.springframework.data.web.PageableDefault",
            "org.springframework.security.access.prepost.PreAuthorize",
            "org.springframework.security.authentication.AuthenticationCredentialsNotFoundException",
            "org.springframework.security.core.userdetails.UserDetails",
            "org.springframework.web.bind.annotation.RestController",
            "studio.one.platform.identity.PrincipalResolver",
            "studio.one.platform.web.dto.ApiResponse"
    })
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".template.web", name = "enabled", havingValue = "true", matchIfMissing = true )
    @Import(TemplateMgmtController.class)
    static class TemplateWebConfig {
        
    }

    private static PersistenceProperties.Type resolveTemplatePersistence(
            TemplateFeatureProperties templateFeatureProperties,
            PersistenceProperties persistenceProperties) {
        PersistenceProperties.Type type = templateFeatureProperties.resolvePersistence(persistenceProperties.getType());
        return type == PersistenceProperties.Type.mybatis ? PersistenceProperties.Type.jdbc : type;
    }
}

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

import java.util.Optional;

import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

import studio.one.application.template.domain.entity.TemplateEntity;
import studio.one.application.template.persistence.repository.TemplateRepository;
import studio.one.application.template.service.TemplatesService;
import studio.one.application.template.service.impl.JdbcTemplatesService;
import studio.one.application.template.service.impl.JpaTemplatesService;
import studio.one.application.template.web.controller.TemplateController;
import studio.one.platform.autoconfigure.PersistenceProperties;
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

@AutoConfiguration
@ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX
        + ".template", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({ TemplateFeatureProperties.class, PersistenceProperties.class })
public class TemplateAutoConfiguration {

    @Configuration
    @ConditionalOnClass(EntityManagerFactory.class)
    @ConditionalOnBean(name = "entityManagerFactory")
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jpa)
    @EnableJpaRepositories(basePackageClasses = TemplateRepository.class)
    @EntityScan(basePackageClasses = TemplateEntity.class)
    static class TemplateJpaConfig {
    }

    @Bean(TemplatesService.SERVICE_NAME)
    @ConditionalOnMissingBean(JpaTemplatesService.class)
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jpa)
    public JpaTemplatesService jpaTemplatesService(
            TemplateRepository templateRepository,
            ObjectProvider<freemarker.template.Configuration> configurationProvider) {
        return new JpaTemplatesService(templateRepository, configurationProvider);
    }

    @Bean(TemplatesService.SERVICE_NAME)
    @ConditionalOnMissingBean(JdbcTemplatesService.class)
    @ConditionalOnTemplatePersistence(PersistenceProperties.Type.jdbc)
    public JdbcTemplatesService jdbcTemplatesService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectProvider<freemarker.template.Configuration> configurationProvider) {
        return new JdbcTemplatesService(jdbcTemplate, Optional.of(configurationProvider.getIfAvailable()));
    }

    @Bean
    @ConditionalOnMissingBean(studio.one.application.template.service.TemplatesService.class)
    public studio.one.application.template.service.TemplatesService templatesService(
            TemplateFeatureProperties templateFeatureProperties,
            PersistenceProperties persistenceProperties,
            ObjectProvider<JpaTemplatesService> jpaProvider,
            ObjectProvider<JdbcTemplatesService> jdbcProvider) {

        PersistenceProperties.Type type = templateFeatureProperties.resolvePersistence(persistenceProperties.getType());
        if (type == PersistenceProperties.Type.jpa) {
            JpaTemplatesService jpa = jpaProvider.getIfAvailable();
            if (jpa == null) {
                throw new IllegalStateException("JPA persistence selected but JpaTemplatesService is not available");
            }
            return jpa;
        }
        if (type == PersistenceProperties.Type.jdbc) {
            JdbcTemplatesService jdbc = jdbcProvider.getIfAvailable();
            if (jdbc == null) {
                throw new IllegalStateException("JDBC persistence selected but JdbcTemplatesService is not available");
            }
            return jdbc;
        }
        throw new IllegalStateException("Unsupported persistence type for template service: " + type);
    }

    @Configuration
    @ConditionalOnProperty(prefix = PropertyKeys.Features.PREFIX + ".template.web", name = "enabled", havingValue = "true", matchIfMissing = true )
    @Import(TemplateController.class)
    static class TemplateWebConfig {
        
    }
}

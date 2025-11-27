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
 *      @file PlatformPropertiesAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.autoconfigure.features.properties;

import javax.persistence.EntityManager;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.EntityScanRegistrarSupport;
import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.features.properties.condition.ConditionalOnPropertiesPersistence;
import studio.one.platform.component.YamlApplicationProperties;
import studio.one.platform.component.properties.domain.service.JdbcApplicationProperties;
import studio.one.platform.component.properties.domain.service.JpaApplicationProperties;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;

/**
 * ApplicationProperties 컴포넌트 자동 등록 클래스.
 * 
 * @author donghyuck, son
 * @since 2025-08-13
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-13  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
@EnableConfigurationProperties({ PersistenceProperties.class, PropertiesProperties.class })
@ConditionalOnProperty(prefix = PropertyKeys.Features.ApplicationProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false )
@RequiredArgsConstructor
@Slf4j
public class PropertiesAutoConfiguration {

    final PersistenceProperties persistence;
    final PropertiesProperties props;   

    @Bean 
    @ConditionalOnPropertiesPersistence(PersistenceProperties.Type.jpa)
    static BeanDefinitionRegistryPostProcessor featureAEntityScanRegistrar() {  
        return EntityScanRegistrarSupport.entityScanRegistrar(
                PropertyKeys.Features.ApplicationProperties.PREFIX + ".entity-packages", "studio.one.platform.component.properties.domain.entity");
    }

    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    @ConditionalOnPropertiesPersistence(PersistenceProperties.Type.jdbc) 
    public ApplicationProperties jdbcApplicationProperties( 
            @Qualifier(ServiceNames.JDBC_TEMPLATE) ObjectProvider<JdbcTemplate> jdbcProvider,
            ApplicationEventPublisher publisher,
            ObjectProvider<I18n> i18nProvider 
    ) {  
        if(log.isDebugEnabled())
            log.debug("Preparing ApplicationProperties resolvePersistence:jdbc"); 
        I18n i18n = I18nUtils.resolve(i18nProvider); 
        return new JdbcApplicationProperties(jdbcProvider.getIfAvailable(), publisher, i18n);
    }

    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    @ConditionalOnPropertiesPersistence(PersistenceProperties.Type.jpa) 
    public ApplicationProperties jpaApplicationProperties(  
            ObjectProvider<EntityManager> emProvider,
            ApplicationEventPublisher publisher,
            ObjectProvider<I18n> i18nProvider 
    ) {  
        if(log.isDebugEnabled())
            log.debug("Preparing ApplicationProperties resolvePersistence:jpa"); 
        I18n i18n = I18nUtils.resolve(i18nProvider); 
        return new JpaApplicationProperties(emProvider.getIfAvailable(), publisher, i18n);
    }

    @ConditionalOnMissingBean
    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    public ApplicationProperties applicationPropertiesFallback(Environment environment, ObjectProvider<I18n> i18nProvider) {
        return new YamlApplicationProperties(environment, I18nUtils.resolve(i18nProvider));
    }

}

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


package studio.echo.platform.autoconfigure;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import studio.echo.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.echo.platform.component.YamlApplicationProperties;
import studio.echo.platform.component.properties.domain.JpaApplicationProperties;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
/**
 * ApplicationProperties 컴포넌트 자동 등록 클래스.
 * 
 * @author  donghyuck, son
 * @since 2025-08-13
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-13  donghyuck, son: 최초 생성.
 * </pre>
 */


@AutoConfiguration
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
public class PlatformPropertiesAutoConfiguration {

    @Bean
    static BeanDefinitionRegistryPostProcessor featureAEntityScanRegistrar() {
        return EntityScanRegistrarSupport.entityScanRegistrar(
            PropertyKeys.Features.ApplicationProperties.PREFIX + ".entity-packages",
            "studio.echo.platform.component.properties.domain.entity" 
        );
    }

    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    @ConditionalOnClass(EntityManagerFactory.class)  
    @ConditionalOnBean(EntityManagerFactory.class)  
    @ConditionalOnProperties(value = {
            @ConditionalOnProperties.Property(name = PropertyKeys.Jpa.ENABLED, havingValue = "true"),
            @ConditionalOnProperties.Property(name = PropertyKeys.Features.ApplicationProperties.ENABLED, havingValue = "true", matchIfMissing = false)
    })
    public ApplicationProperties jpaApplicationProperties(EntityManager em, ApplicationEventPublisher publisher,
            ObjectProvider<I18n> i18nProvider // 있어도/없어도 동작
    ) {
        
        return new JpaApplicationProperties(em, publisher, I18nUtils.resolve(i18nProvider));
    }

    @ConditionalOnMissingBean
    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    public ApplicationProperties applicationPropertiesFallback(Environment environment, ObjectProvider<I18n> i18nProvider) {

        return new YamlApplicationProperties(environment, I18nUtils.resolve(i18nProvider));
    }

}

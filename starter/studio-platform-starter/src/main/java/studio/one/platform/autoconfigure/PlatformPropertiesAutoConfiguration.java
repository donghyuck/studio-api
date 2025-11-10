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

package studio.one.platform.autoconfigure;

import java.util.Objects;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.batch.BatchProperties.Jdbc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.autoconfigure.condition.ConditionalOnProperties;
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
@EnableConfigurationProperties({ PropertiesProperties.class })
@RequiredArgsConstructor
public class PlatformPropertiesAutoConfiguration {

    final PropertiesProperties props;

    @Bean 
    @ConditionalOnProperties.Property(name = PropertyKeys.Persistence.Jpa.ENABLED, havingValue = "true")
    static BeanDefinitionRegistryPostProcessor featureAEntityScanRegistrar() {  
        return EntityScanRegistrarSupport.entityScanRegistrar(
                PropertyKeys.Features.ApplicationProperties.PREFIX + ".entity-packages", "studio.one.platform.component.properties.domain.entity");
    }

    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    @ConditionalOnClass(EntityManagerFactory.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperties(value = {
            @ConditionalOnProperties.Property(name = PropertyKeys.Persistence.Jpa.ENABLED, havingValue = "true"),
            @ConditionalOnProperties.Property(name = PropertyKeys.Features.ApplicationProperties.ENABLED, havingValue = "true", matchIfMissing = false)
    })
    public ApplicationProperties jpaApplicationProperties(
            ObjectProvider<EntityManager> emProvider,
            @Qualifier(ServiceNames.JDBC_TEMPLATE) ObjectProvider<JdbcTemplate> jdbcProvider,
            ApplicationEventPublisher publisher,
            ObjectProvider<I18n> i18nProvider // 있어도/없어도 동작
    ) { 
        I18n i18n = I18nUtils.resolve(i18nProvider); 
        return switch (props.getPersistence().getType()) {
            case jpa -> {
                var em = Objects.requireNonNull(emProvider.getIfAvailable(), "type=jpa 인데 EntityManager 가 없습니다.");
                yield new JpaApplicationProperties(em, publisher, i18n);
            }
            case jdbc -> {
                var jdbc = Objects.requireNonNull(jdbcProvider.getIfAvailable(), "type=" + props.getPersistence().getType() + " 인데 JdbcTemplate 이 없습니다.");
                yield new JdbcApplicationProperties(jdbc, publisher, i18n);
            }
            default -> throw new IllegalStateException("Unexpected value: " + props.getPersistence().getType());
        };

    }

    @ConditionalOnMissingBean
    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    public ApplicationProperties applicationPropertiesFallback(Environment environment, ObjectProvider<I18n> i18nProvider) {
        return new YamlApplicationProperties(environment, I18nUtils.resolve(i18nProvider));
    }

}

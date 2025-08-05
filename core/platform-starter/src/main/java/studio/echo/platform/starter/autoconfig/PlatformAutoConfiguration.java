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
 *      @file PlatformAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.echo.platform.starter.autoconfig;

import javax.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.BasePackage;
import studio.echo.platform.component.I18nImpl;
import studio.echo.platform.component.RepositoryImpl;
import studio.echo.platform.component.YamlApplicationProperties;
import studio.echo.platform.component.properties.jpa.JpaApplicationProperties;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.I18n;
import studio.echo.platform.service.Repository;
import studio.echo.platform.starter.autoconfig.condition.ConditionalOnProperties;

/**
 *
 * @author donghyuck, son
 * @since 2025-07-25
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-25  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EntityScan(basePackageClasses = {
        BasePackage.class
})
@RequiredArgsConstructor
@EnableConfigurationProperties( {I18nProperties.class, FeaturesProperties.class } ) 
@Slf4j
public class PlatformAutoConfiguration {

    @Bean
    public MessageSource messageSource(I18nProperties props) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(props.getResources().toArray(new String[0]));
        messageSource.setDefaultEncoding(props.getEndoding());
        messageSource.setFallbackToSystemLocale(props.isFallbackToSystemLocale());
        messageSource.setCacheSeconds(props.getCacheSeconds()); // JAR 환경에선 변경 없음 
        return messageSource;
    }

    @ConditionalOnMissingBean
    @Bean(name = ServiceNames.I18N)
    public I18n i18n(MessageSource messageSource) {
        return new I18nImpl(messageSource);
    }

    @ConditionalOnProperties( 
      value = {
          @ConditionalOnProperties.Property(name = PropertyKeys.Persistence.JPA, havingValue = "true"),
          @ConditionalOnProperties.Property(name = PropertyKeys.Features.ApplicationProperties.ENABLED, havingValue = "true", matchIfMissing = false)
      }
    )
    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    public ApplicationProperties applicationProperties(EntityManager em, ApplicationEventPublisher publisher, I18n i18n) {
        return new JpaApplicationProperties(em, publisher, i18n);
    }

    @ConditionalOnMissingBean
    @Bean(name = ServiceNames.APPLICATION_PROPERTIES)
    public ApplicationProperties applicationPropertiesFallback(Environment environment, I18n i18n) {
        return new YamlApplicationProperties(environment, i18n);
    }

    @Bean(name = ServiceNames.REPOSITORY)
    @ConditionalOnMissingBean
    public Repository repository(
            @Qualifier(ServiceNames.APPLICATION_PROPERTIES) ApplicationProperties applicationProperties,
            @Qualifier(ServiceNames.I18N) I18n i18n,
            Environment env,
            ApplicationEventPublisher applicationEventPublisher) {
        return new RepositoryImpl(applicationProperties, i18n, env, applicationEventPublisher);
    }

}

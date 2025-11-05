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

package studio.one.platform.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.RepositoryImpl;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.I18n;
import studio.one.platform.service.Repository;
import studio.one.platform.util.I18nUtils;

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
@RequiredArgsConstructor
@EnableConfigurationProperties( { FeaturesProperties.class } ) 
@AutoConfigureAfter({ PlatformPropertiesAutoConfiguration.class, PlatformI18nAutoConfiguration.class })
@Slf4j
public class PlatformRepositoryAutoConfiguration {
    /**
     * ==============================
     * Repository Component
     */
    @Bean(name = ServiceNames.REPOSITORY)
    @ConditionalOnMissingBean
    public Repository repository(
            @Qualifier(ServiceNames.APPLICATION_PROPERTIES) ApplicationProperties applicationProperties,
            ObjectProvider<I18n> i18nProvider,
            Environment env,
            ApplicationEventPublisher applicationEventPublisher) {
        return new RepositoryImpl(applicationProperties, I18nUtils.resolve(i18nProvider), env, applicationEventPublisher);
    }

}

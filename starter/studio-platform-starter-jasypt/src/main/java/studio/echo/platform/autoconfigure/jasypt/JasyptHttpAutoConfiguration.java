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
 *      @file JasyptHttpAutoConfiguration.java
 *      @date 2025
 *
 */


package studio.echo.platform.autoconfigure.jasypt;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
/**
 *
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 * </pre>
 */


@AutoConfiguration
@EnableConfigurationProperties(JasyptProperties.class)
@ConditionalOnBean(StringEncryptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperties(
    prefix = PropertyKeys.Features.Jasypt.PREFIX,
    value = {
    @ConditionalOnProperties.Property( name = "enabled", havingValue = "true"),
    @ConditionalOnProperties.Property( name = "http.enabled", havingValue = "true", matchIfMissing = false)    
})
@AutoConfigureAfter(JasyptAutoConfiguration.class)
@Slf4j
public class JasyptHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JasyptHttpController.class) // 앱에서 커스터마이즈 가능
    public JasyptHttpController jasyptHttpController(StringEncryptor encryptor,
                                                     JasyptProperties props, ObjectProvider<I18n> i18nProvider) { 
        return new JasyptHttpController(encryptor, props.getHttp(), I18nUtils.resolve(i18nProvider));
    }
 
}




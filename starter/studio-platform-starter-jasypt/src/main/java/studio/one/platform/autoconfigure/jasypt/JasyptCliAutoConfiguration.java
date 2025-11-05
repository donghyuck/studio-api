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
 *      @file JasyptCliAutoConfiguration.java
 *      @date 2025
 *
 */


package studio.one.platform.autoconfigure.jasypt;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 *
 * @author  donghyuck, son
 * @since 2025-08-11
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-11  donghyuck, son: 최초 생성.
 * </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties(JasyptProperties.class)
@ConditionalOnBean(StringEncryptor.class) // encryptor 빈 있어야 실행
@ConditionalOnNotWebApplication   // ← 웹 컨텍스트가 아닐 때만 러너 등록
@ConditionalOnProperty(
        prefix = "studio.features.jasypt.cli",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JasyptCliAutoConfiguration {

    @Bean
    public ApplicationRunner jasyptCliRunner(StringEncryptor encryptor, JasyptProperties props) {
        return new JasyptCliRunner(encryptor);
    }

}
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
 *      @file JasyptAutoConfiguration.java
 *      @date 2025
 *
 */


package studio.echo.platform.autoconfigure.jasypt;

import java.security.Security;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.service.I18n;
import studio.echo.platform.util.I18nUtils;
import studio.echo.platform.util.LogUtils;

/**
 * Jasypt를 사용하여 프로퍼티 암호화를 자동 구성하는 클래스입니다.
 * <p>
 * 이 클래스는 다음 조건이 모두 충족될 때 활성화됩니다.
 * <ul>
 * <li>{@code studio.echo.jasypt.enabled} 프로퍼티가 {@code true}로 설정되어 있어야 합니다.</li>
 * <li>Jasypt 라이브러리 ({@code org.jasypt.encryption.StringEncryptor})가 클래스패스에 존재해야
 * 합니다.</li>
 * </ul>
 * </p>
 *
 * <p>
 * 이 클래스는 다음 빈을 제공합니다.
 * <ul>
 * <li>{@code jasyptStringEncryptor}: Jasypt를 사용하여 문자열을 암호화/복호화하는
 * {@link StringEncryptor} 빈입니다.</li>
 * </ul>
 * </p>
 *
 * <p>
 * {@code encryptor.bean} 프로퍼티를 사용하여 {@code jasyptStringEncryptor} 빈에 대한 별칭을 등록할
 * 수 있습니다.
 * </p>
 * 
 * @author donghyuck, son
 * @since 2025-08-11
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-11  donghyuck, son: 최초 생성.
 *          </pre>
 */

@AutoConfiguration
@EnableConfigurationProperties({ JasyptProperties.class })
@Slf4j
// ① 프로퍼티 토글로 ON일 때만
@ConditionalOnProperty(prefix = PropertyKeys.Features.Jasypt.PREFIX, name = "enabled", havingValue = "true")
// ② Jasypt 클래스가 클래스패스에 있을 때만 (starter 또는 jasypt-core 존재)
@ConditionalOnClass(org.jasypt.encryption.StringEncryptor.class) // Jasypt 모듈이 있을 때만 설정 적용 
public class JasyptAutoConfiguration {

 
    // ③ 이미 다른 곳에서 StringEncryptor를 정의했다면 건드리지 않음
    @Bean(name = "jasyptStringEncryptor")
    @ConditionalOnMissingBean(StringEncryptor.class)
    public StringEncryptor stringEncryptor(JasyptProperties props) {
        var e = props.getEncryptor();
        var cfg = new SimpleStringPBEConfig();
        cfg.setPassword(e.getPassword());
        cfg.setAlgorithm(e.getAlgorithm());
        if (e.getProviderName() != null)
            cfg.setProviderName(e.getProviderName());
        if (e.getKeyObtentionIterations() > 1000)
            cfg.setKeyObtentionIterations(e.getKeyObtentionIterations());
        if (e.getPoolSize() > 0)
            cfg.setPoolSize(e.getPoolSize());
        if (e.getSaltGeneratorClassname() != null)
            cfg.setSaltGeneratorClassName(e.getSaltGeneratorClassname());
        if (e.getIvGeneratorClassname() != null)
            cfg.setIvGeneratorClassName(e.getIvGeneratorClassname());
        if (e.getStringOutputType() != null)
            cfg.setStringOutputType(e.getStringOutputType());
        var enc = new PooledPBEStringEncryptor();
        enc.setConfig(cfg);
        return enc;
    }

    /** encryptor.bean 지정 시, 기본 빈에 별칭 추가 (콜론/대시 허용) */
    @Bean
    public static BeanFactoryPostProcessor jasyptBeanAlias(Environment env) {
        return bf -> {
            String alias = env.getProperty(PropertyKeys.Features.Jasypt.ENCRYPTOR + ".bean");
            if (alias == null || alias.trim().isEmpty() || "jasyptStringEncryptor".equals(alias)) return;

            if (bf instanceof BeanDefinitionRegistry) {
                var reg = (BeanDefinitionRegistry) bf;
                boolean inUse = reg.isBeanNameInUse(alias);
                if (!inUse) {
                    reg.registerAlias("jasyptStringEncryptor", alias.trim());
                }
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = PropertyKeys.Features.Jasypt.ENCRYPTOR, name = "provider-name", havingValue = "BC", matchIfMissing = false)
    @ConditionalOnClass(name = "org.bouncycastle.jce.provider.BouncyCastleProvider")
    public static BeanFactoryPostProcessor registerBouncyCastle( ObjectProvider<I18n> i18nProvider  ) {
        return bf -> {
            if (Security.getProvider("BC") == null) { 
                try {
                    I18n i18n = I18nUtils.resolve(i18nProvider); 
                    Class<?> providerClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
                    Security.addProvider((java.security.Provider) providerClass.getDeclaredConstructor().newInstance());
                    
                    log.info(LogUtils.red(  i18n.get("success.jasypt.security.provider.added", providerClass.getName(), "BC", providerClass.getPackage().getImplementationVersion()) )); 
                } catch (Exception e) {
                    log.error("Failed to register Bouncy Castle Provider.", e);  // 예외 포함 로그
                }
            }
        };
    }

}

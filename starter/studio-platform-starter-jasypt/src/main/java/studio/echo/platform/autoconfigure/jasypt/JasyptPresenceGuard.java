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
 *      @file JasyptPresenceGuard.java
 *      @date 2025
 *
 */



package studio.echo.platform.autoconfigure.jasypt;

import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
/**
 * Jasypt 설정이 올바르게 구성되었는지 확인하고, 필요한 라이브러리가 클래스패스에 있는지 검사하는 클래스입니다.
 * <p>
 * 이 클래스는 Jasypt 자동 구성 전에 실행되어, Jasypt 사용에 필요한 조건들이 충족되었는지 사전 검사합니다.
 * 만약 필요한 조건이 충족되지 않으면, 예외를 발생시키거나 경고 로그를 출력하여 사용자에게 문제 해결 방법을 안내합니다.
 * </p>
 *
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>Jasypt 라이브러리 존재 여부 확인</li>
 *   <li>Bouncy Castle Provider 존재 여부 확인 (BC Provider 사용 시)</li>
 *   <li>암호화 비밀번호 설정 여부 확인</li>
 *   <li>빈 별칭 형식 유효성 검사</li>
 * </ul>
 *
 * <p><b>사용 방법:</b></p>
 * <p>
 * 이 클래스는 Spring Boot 자동 구성 메커니즘에 의해 자동으로 실행되므로, 별도의 설정 없이 Jasypt 사용 전에 필요한 검사를 수행합니다.
 * 만약 검사 결과에 따라 예외가 발생하거나 경고 로그가 출력되면, 문제 해결 방법을 참고하여 Jasypt 설정을 올바르게 구성해야 합니다.
 * </p>
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
@AutoConfigureBefore(JasyptAutoConfiguration.class)
@ConditionalOnProperty(prefix = PropertyKeys.Features.Jasypt.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JasyptProperties.class)
@NoArgsConstructor(access = lombok.AccessLevel.PUBLIC)
@Slf4j
@SuppressWarnings("java:S1118")
public class JasyptPresenceGuard {

    private static final String TARGET_ENCRYPTOR = "org.jasypt.encryption.StringEncryptor";
    private static final String TARGET_BC = "org.bouncycastle.jce.provider.BouncyCastleProvider";

    // 프로퍼티 키들
    private static final String PFX = PropertyKeys.Features.Jasypt.PREFIX; // studio.features.jasypt
    private static final String KEY_FAIL_IF_MISSING = PFX + ".fail-if-missing";
    private static final String KEY_ENCRYPTOR_PASSWORD = PFX + ".encryptor.password";
    private static final String KEY_ENCRYPTOR_PROVIDER = PFX + ".encryptor.provider-name";
    private static final String KEY_ENCRYPTOR_BEAN_ALIAS = PFX + ".encryptor.bean";

    @Bean
    public static BeanFactoryPostProcessor jasyptClasspathGuard(
            Environment env,
            @Qualifier(ServiceNames.JASYPT_MESSAGE_ACCESSOR) ObjectProvider<MessageSourceAccessor> messagesProvider) {
        return bf -> {
            MessageSourceAccessor msg = messagesProvider.getIfAvailable();
            boolean failIfMissing = env.getProperty(KEY_FAIL_IF_MISSING, Boolean.class, true);
            String password = env.getProperty(KEY_ENCRYPTOR_PASSWORD);
            String provider = env.getProperty(KEY_ENCRYPTOR_PROVIDER);
            String alias = env.getProperty(KEY_ENCRYPTOR_BEAN_ALIAS);
            StringBuilder help = new StringBuilder();
            boolean hasError = false;
            hasError |= checkJasyptLibrary(msg, help);
            hasError |= checkBouncyCastle(msg, help, provider);
            hasError |= checkEncryptionPassword(msg, help, password);
            hasError |= checkAliasFormat(msg, help, alias);
            if (hasError) {
                handleError(msg, help, failIfMissing);
            }
        };
    }

    private static boolean checkJasyptLibrary(MessageSourceAccessor msg, StringBuilder help) {
        if (!ClassUtils.isPresent(TARGET_ENCRYPTOR, JasyptPresenceGuard.class.getClassLoader())) {
            help.append("\n").append(
                    m(msg, "error.jasypt.missing.lib",
                            "※ Jasypt 라이브러리가 없습니다: {0}\n  해결: com.github.ulisesbocchio:jasypt-spring-boot-starter(boot 2.x: 3.0.4) 추가",
                            TARGET_ENCRYPTOR));
            return true;
        }
        return false;
    }

    private static boolean checkBouncyCastle(MessageSourceAccessor msg, StringBuilder help, String provider) {
        boolean needBC = "BC".equalsIgnoreCase(provider);
        if (needBC && !ClassUtils.isPresent(TARGET_BC, JasyptPresenceGuard.class.getClassLoader())) {
            help.append("\n").append(
                    m(msg, "error.jasypt.missing.bc", "※ BouncyCastle가 없습니다.\n  설정: " + KEY_ENCRYPTOR_PROVIDER + "=BC\n  해결: org.bouncycastle:bcprov-jdk15on 추가"));
            return true;
        }
        return false;
    }

    private static boolean checkEncryptionPassword(MessageSourceAccessor msg, StringBuilder help, String password) {
        if (!StringUtils.hasText(password)) {
            help.append("\n").append(
                    m(msg, "error.jasypt.missing.password", "※ 암호화 비밀번호가 없습니다.\n  설정 키: " + KEY_ENCRYPTOR_PASSWORD + "\n  예: export JASYPT_ENCRYPTOR_PASSWORD=yourSecret"));
            return true;
        }
        return false;
    }

    // 허용 문자: 영문/숫자/점(.)/대시(-)/언더스코어(_)/콜론(:)
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static boolean checkAliasFormat(MessageSourceAccessor msg, StringBuilder help, String alias) {
        if (StringUtils.hasText(alias) && !ALIAS_PATTERN.matcher(alias).matches()) {
        help.append("\n").append(m(
            msg,
            "error.jasypt.invalid.alias",
            "※ 빈 별칭 형식 오류: \"{0}\"\n  허용: 영문/숫자/점/대시/언더스코어/콜론 (공백·쉼표 금지)",
            alias
        ));
        return true; // 에러 발생
    }
    return false; // OK
    }

    private static void handleError(MessageSourceAccessor msg, StringBuilder help, boolean failIfMissing) {
        String title = m(msg, "error.jasypt.title", "Jasypt 설정 점검이 필요합니다.");
        String hint = m(msg, "error.jasypt.hint.suppress",
                "※ 완화 옵션:\n  - 경고만 보려면: " + KEY_FAIL_IF_MISSING + "=false\n  - 기능 끄기: " + PFX + ".enabled=false");
        String full = title + help.toString() + "\n" + hint;
        if (failIfMissing)
            throw new IllegalStateException(full);
        else
            log.warn(full);
    }

    private static String m(MessageSourceAccessor ms, String code, String fallback, Object... args) {
        if (ms == null)
            return formatFallback(fallback, args);
        try {
            return ms.getMessage(code, args, fallback);
        } catch (Exception ex) {
            return formatFallback(fallback, args);
        }
    }

    private static String formatFallback(String template, Object... args) {
        // 간단한 {0} 치환
        String s = template;
        for (int i = 0; i < (args == null ? 0 : args.length); i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }
}

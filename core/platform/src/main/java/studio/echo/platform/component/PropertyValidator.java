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
 *      @file PropertyValidator.java
 *      @date 2025
 *
 */
package studio.echo.platform.component;

import java.util.regex.Pattern;

import org.springframework.util.StringUtils;


/**
 * 프로퍼티 이름이 민감한 정보(예: password, secret, key, token)와 관련되어 있는지 판별하는 유틸리티 클래스입니다.
 * <p>
 * 이 클래스는 인스턴스화할 수 없으며, 정적 메서드만 제공합니다.
 * </p>
 *
 * <ul>
 * <li>민감한 프로퍼티 이름을 정규표현식으로 검사합니다.</li>
 * <li>정확히 "password", "secret", "key", "token"과 일치하는 경우만 민감한 정보로 간주합니다.</li>
 * <li>유효성 검사가 필요한 곳에서 정적 메서드로 호출하여 사용할 수 있습니다.</li>
 * </ul>
 * 
 * @author donghyuck, son
 * @since 2025-07-14
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-14  donghyuck, son: 최초 생성.
 *          </pre>
 */

public class PropertyValidator {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile("\\b(password|secret|key|token)\\b",
            Pattern.CASE_INSENSITIVE);

    private PropertyValidator() {
    }

    public static boolean isSensitiveProperty(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        return SENSITIVE_PATTERN.matcher(name).matches();
    }
}

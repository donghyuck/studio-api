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
 *      @file SecurityProperties.java
 *      @date 2025
 *
 */
package studio.one.platform.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

/**
 * 보안(Security) 관련 전역 설정 정보를 담는 프로퍼티 클래스입니다.
 * <p>
 * <ul>
 * <li>passwordEncoder: 사용할 암호화 방식(bcrypt, scrypt, pbkdf2 등, 기본값은 bcrypt)</li>
 * <li>pbkdf2Secret, pbkdf2Iterations, pbkdf2HashWidth: PBKDF2 암호화 관련 상세 설정</li>
 * <li>cors: CORS(Cross-Origin Resource Sharing) 정책 설정</li>
 * <li>jwt: JWT(Json Web Token) 관련 설정</li>
 * </ul>
 * </p>
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

@ConfigurationProperties(prefix = PropertyKeys.Security.PREFIX)
@Getter
@Setter
public class SecurityProperties {

    private boolean enabled = false;
    private boolean failIfMissing = true;

    private String defaultRole;

    // PBKDF2 관련 설정
    private String pbkdf2Secret;
    private int pbkdf2Iterations = 185000;
    private int pbkdf2HashWidth = 256;
    private String[] defaultRoles = new String[] {};

    private CorsProperties cors = new CorsProperties();
    private JwtProperties jwt = new JwtProperties();
    private SecurityPermitProperties permit = new SecurityPermitProperties();
    private PasswordEncoderProperties passwordEncoder = new PasswordEncoderProperties();

}

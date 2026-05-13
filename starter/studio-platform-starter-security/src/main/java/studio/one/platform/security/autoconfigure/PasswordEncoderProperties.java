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
 *      @file PasswordEncoderProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.security.autoconfigure;

import java.util.Map;

import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author donghyuck, son
 * @since 2025-08-18
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-18  donghyuck, son: 최초 생성.
 *          </pre>
 */

@Getter
@Setter
public class PasswordEncoderProperties {

    private Algorithm algorithm = Algorithm.BCRYPT;

    /** 공통/선택 옵션 */
    private String secret;

    /** PBKDF2 전용 */
    @Positive
    private Integer iterations;
    @Positive
    private Integer hashWidth;

    /** BCrypt 전용 */
    @Min(4)
    private Integer bcryptStrength;  // 기본 10 추천 (4~31)

    /** PBKDF2 전용: HMAC 알고리즘 선택 */
    private Pbkdf2Algo pbkdf2Algo = Pbkdf2Algo.PBKDF2WithHmacSHA256;

    /** 커스텀용(확장 포인트) */
    private Map<String, String> custom;

    public enum Algorithm {
        BCRYPT, PBKDF2, SHA256, CUSTOM
    }

    @SuppressWarnings("java:S115")
    public enum Pbkdf2Algo {
        PBKDF2WithHmacSHA1,
        PBKDF2WithHmacSHA256,
        PBKDF2WithHmacSHA512
    }
}

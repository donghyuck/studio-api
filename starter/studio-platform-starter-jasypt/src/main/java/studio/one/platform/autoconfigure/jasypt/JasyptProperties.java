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
 *      @file JasyptProperties.java
 *      @date 2025
 *
 */


package studio.one.platform.autoconfigure.jasypt;
 

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

/**
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
 
@ConfigurationProperties(prefix = PropertyKeys.Features.Jasypt.PREFIX)
@Data
@Validated
public class JasyptProperties extends FeatureToggle {

    @Valid
    private EncryptorProperties encryptor = new EncryptorProperties();

    @Valid
    private JasyptHttpEndpointProperties http = new JasyptHttpEndpointProperties();
    
    @Getter
    @Setter
    @NoArgsConstructor
    public static class EncryptorProperties {

        /** StringEncryptor 빈 이름(선택). 비우면 기본 이름 사용 */ 
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "bean name must match ^[A-Za-z0-9._:-]+$")
        private String bean;

        @NotBlank
        private String password ; // 디폴트 암호화 비밀번호

        @NotBlank
        private String algorithm = "PBEWithSHA256And128BitAES-CBC-BC"; // 디폴트 알고리즘
        private String providerName = "BC"; // Bouncy Castle 기본 제공자

        @Positive
        private int keyObtentionIterations = 1000; // 기본 키 반복 횟수

        @Positive
        private int poolSize = 1; // 기본 풀 크기

        @NotBlank
        private String saltGeneratorClassname = "org.jasypt.salt.RandomSaltGenerator"; // 기본 Salt 생성기

        private String ivGeneratorClassname = "org.jasypt.iv.RandomIvGenerator";

        @NotBlank
        private String stringOutputType = "base64"; // 기본 출력 타입 (base64)

    }

    @Getter
    @Setter
    @NoArgsConstructor
    public class JasyptHttpEndpointProperties {

        /** 기능 스위치: 기본 false */
        private boolean enabled = false;

        /** 베이스 경로 (동적 매핑에 사용) */
        @NotBlank
        private String basePath = "/internal/jasypt";

        /** 토큰 요구 여부 (기본 true) */
        private boolean requireToken = true;

        /** 토큰을 환경변수에서 읽을 때의 키 (기본 JASYPT_HTTP_TOKEN) */
        @NotBlank
        private String tokenEnv = "JASYPT_HTTP_TOKEN";

        /** (선택) 토큰을 프로퍼티로 직접 지정하고 싶을 때 */
        private String tokenValue;
    }

}

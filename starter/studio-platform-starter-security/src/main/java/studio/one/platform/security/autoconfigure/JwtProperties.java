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
 *      @file JwtProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.security.autoconfigure;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import studio.one.base.security.jwt.JwtConfig;
import studio.one.platform.autoconfigure.PersistenceProperties;

/**
 * JWT(Json Web Token) 관련 설정 정보를 담는 클래스입니다.
 * <p>
 * <ul>
 * <li>secret: JWT 서명에 사용할 시크릿 키</li>
 * <li>accessTokenExpiry: 액세스 토큰 만료 시간(밀리초)</li>
 * <li>issuer: 토큰 발급자(issuer) 정보</li>
 * <li>refreshTokenExpiry: 리프레시 토큰 만료 시간(밀리초)</li>
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

@Getter
@Setter
public class JwtProperties implements JwtConfig {

    private boolean enabled = false;

    private String secret;

    private String issuer;

    private Duration accessTtl = Duration.ofMinutes(15);

    private Duration refreshTtl = Duration.ofMinutes(7);

    private Duration rotationGrace = Duration.ZERO;

    /**
     * Optional persistence override for refresh tokens.
     */
    private PersistenceProperties.Type persistence;

    private String header = "Authorization";

    private String prefix = "Bearer";

    private String claimAuthorities = "authorities";

    private String refreshCookieName = "refresh_token";

    private String cookiePath = "/api/auth";
    
    private boolean cookieSecure = true;
    
    private String cookieSameSite = "Strict";

    @Getter
    @Setter
    public static class Endpoints {
        private boolean loginEnabled = true;
        private boolean refreshEnabled = true;
        private String basePath = "/api/auth";

    }

    private Endpoints endpoints = new Endpoints();

    /** 인증 없이 접근 허용할 패턴 */
    private List<String> permit = Arrays.asList("/api/auth/**");

    public PersistenceProperties.Type resolvePersistence(PersistenceProperties.Type globalDefault) {
        if (persistence != null) {
            return persistence;
        }
        return globalDefault != null ? globalDefault : PersistenceProperties.Type.jpa;
    }

}

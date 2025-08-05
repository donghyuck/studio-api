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


package studio.echo.platform.starter.autoconfig;

import lombok.Getter;
import lombok.Setter;

/**
 * JWT(Json Web Token) 관련 설정 정보를 담는 클래스입니다.
 * <p>
 * <ul>
 *   <li>secret: JWT 서명에 사용할 시크릿 키</li>
 *   <li>accessTokenExpiry: 액세스 토큰 만료 시간(밀리초)</li>
 *   <li>issuer: 토큰 발급자(issuer) 정보</li>
 *   <li>refreshTokenExpiry: 리프레시 토큰 만료 시간(밀리초)</li>
 * </ul>
 * </p>
 * 
 * @author  donghyuck, son
 * @since 2025-07-25
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-25  donghyuck, son: 최초 생성.
 * </pre>
 */


@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long accessTokenExpiry; // in milliseconds
    private String issuer;
    private long refreshTokenExpiry ; // in milliseconds
}


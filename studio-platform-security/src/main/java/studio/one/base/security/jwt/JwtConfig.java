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
 *      @file JwtConfig.java
 *      @date 2025
 *
 */

package studio.one.base.security.jwt;

import java.time.Duration;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-30
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-30  donghyuck, son: 최초 생성.
 * </pre>
 */

public interface JwtConfig {


    Duration getAccessTtl();

    Duration getRefreshTtl();

    Duration getRotationGrace();

    String getHeader();

    String getPrefix();

    String getClaimAuthorities(); 

    String getRefreshCookieName(); 

    String getCookiePath();
    
    boolean isCookieSecure ();
    
    String getCookieSameSite();

    String getSecret();

    String getIssuer();

    
}

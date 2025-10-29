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
 *      @file RefreshTokenStore.java
 *      @date 2025
 *
 */

package studio.echo.base.security.jwt.refresh;

import java.time.Instant;

/**
 *
 * @author donghyuck, son
 * @since 2025-09-30
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-30  donghyuck, son: 최초 생성.
 *          </pre>
 */

public interface RefreshTokenStore {

    /** raw 토큰(랜덤 문자열) 발급 & 저장, 만료 시각 반환 */
    String mint(Long userId, Instant expiresAt);

     String mint(Long userId);

    /** 검증 & 회전: 유효하면 기존 폐기 후 새 raw 발급 */
    String rotate(String raw);

    /** 현재 raw 무효화(로그아웃) */
    void revoke(String raw);

    /** raw → userId 조회 */
    Long resolveUserId(String raw);
}

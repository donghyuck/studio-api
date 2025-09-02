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
 *      @file SecurityPermitProperties.java
 *      @date 2025
 *
 */

package studio.echo.platform.security.autoconfigure;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 경로와 역할별 접근 제어(role based) 설정 정보를 담는 클래스입니다.
 * <p>
 * <ul>
 * <li>permitAll: 인증 없이 접근을 허용할 경로 목록</li>
 * <li>role: 각 경로별로 허용할 역할(권한) 목록을 매핑</li>
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
public class SecurityPermitProperties {
    private List<String> permitAll;
    private Map<String, List<String>> role;
}

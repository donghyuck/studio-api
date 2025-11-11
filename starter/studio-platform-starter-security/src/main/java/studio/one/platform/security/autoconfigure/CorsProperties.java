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
 *      @file CorsProperties.java
 *      @date 2025
 *
 */


package studio.one.platform.security.autoconfigure;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
/**
 * CORS(Cross-Origin Resource Sharing) 정책 설정 정보를 담는 클래스입니다.
 * <p>
 * <ul>
 *   <li>allowedOrigins: 허용할 오리진(도메인) 목록</li>
 *   <li>allowedMethods: 허용할 HTTP 메서드 목록</li>
 *   <li>allowedHeaders: 허용할 요청 헤더 목록</li>
 *   <li>exposedHeaders: 응답에서 노출할 헤더 목록</li>
 *   <li>allowCredentials: 자격 증명(쿠키 등) 허용 여부</li>
 *   <li>maxAge: preflight 요청 캐시 유효 시간(초)</li>
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
public class CorsProperties {
    private boolean enabled = true;
    private List<String> allowedOrigins;
    private List<String> allowedOriginPatterns;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private Boolean allowCredentials;
    private Long maxAge;

}

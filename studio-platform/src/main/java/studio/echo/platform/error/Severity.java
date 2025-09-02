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
 *      @file Severity.java
 *      @date 2025
 *
 */


package studio.echo.platform.error;
/**
 * 심각도 타입.
 * 
 * @author  donghyuck, son
 * @since 2025-08-12
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-12  donghyuck, son: 최초 생성.
 * </pre>
 */


public enum Severity {
    INFO, // 흐름 안내/사소한 비즈 규칙: 보통 2xx/4xx
    WARN, // 예상 가능한 문제/제약: 4xx
    ERROR // 시스템/비예상 오류: 5xx
}

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
 *      @file Message.java
 *      @date 2025
 *
 */


package studio.one.platform.web.annotation;

import java.lang.annotation.*;
/**
 * 컨트롤러 메서드에서 성공 메시지를 바인딩하기 위한 어노테이션.
 * 메시지 키와 함께 SpEL 기반 인자들을 사용할 수 있습니다.
 *
 * 예시:
 * @Message(value = "success.user.created", args = {"#user.username"})
 * @Message(value = "success.user.created" )
 * 
 * @author  donghyuck, son
 * @since 2025-07-17
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-17  donghyuck, son: 최초 생성.
 * </pre>
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Message {
    /**
     * 메시지 키 (예: success.user.created)
     */
    String value();

    /**
     * 메시지 포맷에 들어갈 인자들 (Spring Expression Language 기반)
     * 예: { "#user.username", "#id" }
     */
    String[] args() default {};
}

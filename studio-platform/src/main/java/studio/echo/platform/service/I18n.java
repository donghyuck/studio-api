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
 *      @file I18n.java
 *      @date 2025
 *
 */

package studio.echo.platform.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;

/**
 * 국제화(I18n)를 지원하기 위한 인터페이스.
 * <p>
 * 이 인터페이스는 애플리케이션이 다양한 언어와 지역에 따라 다른 메시지를 제공할 수 있도록 설계되었습니다.
 * </p>
 * 
 * @author donghyuck, son
 * @since 2025-08-13
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-13  donghyuck, son: 최초 생성.
 * 
 *          </pre>
 */
@FunctionalInterface
public interface I18n {

    /**
     * 지정된 로캘과 인수를 사용하여 메시지를 조회합니다.
     *
     * @param code   메시지 코드 (null 불가)
     * @param args   메시지 포맷팅에 사용될 인수 배열.
     *               <ul>
     *                 <li>{@code null} → 인수 없음으로 처리</li>
     *                 <li>빈 배열 → 빈 값으로 포맷팅</li>
     *               </ul>
     * @param locale 조회할 대상 로캘 (null 불가)
     * @return 국제화된 메시지
     * @throws org.springframework.context.NoSuchMessageException
     *         메시지를 찾을 수 없는 경우
     */
    String get(String code, @Nullable Object[] args, Locale locale);

    /**
     * 현재 스레드 로캘({@link LocaleContextHolder})을 기준으로 메시지를 조회합니다.
     *
     * @param code 메시지 코드 (null 불가)
     * @param args 메시지 인수 배열. {@code null} 허용.
     * @return 국제화된 메시지
     */
    default String get(String code, @Nullable Object... args) {
        return get(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * 메시지를 찾을 수 없으면 지정한 기본 메시지를 반환합니다.
     *
     * @param code           메시지 코드
     * @param defaultMessage 기본 반환 메시지
     * @param args           메시지 인수 배열. {@code null} 허용.
     * @return 국제화된 메시지 또는 기본 메시지
     */
    default String getOrDefault(String code, String defaultMessage, @Nullable Object... args) {
        try {
            return get(code, args);
        } catch (Exception ex) {
            return defaultMessage;
        }
    }

    /**
     * 메시지를 Optional 로 감싸서 반환합니다.
     *
     * @param code 메시지 코드
     * @param args 메시지 인수 배열. {@code null} 허용.
     * @return 메시지가 존재하면 Optional, 없으면 Optional.empty()
     */
    default Optional<String> tryGet(String code, @Nullable Object... args) {
        try {
            return Optional.ofNullable(get(code, args));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 명시 로캘 기반 조회 + 기본 메시지 폴백.
     *
     * @param code           메시지 코드
     * @param args           메시지 인수 배열. {@code null} 허용.
     * @param locale         조회할 로캘
     * @param defaultMessage 기본 메시지
     * @return 메시지 또는 기본 메시지
     */
    default String getOrDefault(String code, @Nullable Object[] args, Locale locale, String defaultMessage) {
        try {
            return get(code, args, locale);
        } catch (Exception ex) {
            return defaultMessage;
        }
    }
    
}

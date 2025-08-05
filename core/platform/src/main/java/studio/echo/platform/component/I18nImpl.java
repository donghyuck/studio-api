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
 *      @file I18nImpl.java
 *      @date 2025
 *
 */

package studio.echo.platform.component;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.service.I18n;

/**
 * I18n 인터페이스 구현체. Spring의 MessageSource 기반으로 메시지를 로드.
 * 
 * @author  donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-21  donghyuck, son: 최초 생성.
 * </pre>
 */

@RequiredArgsConstructor
@Slf4j
public class I18nImpl implements I18n {
 
    private final MessageSource messageSource;

    /**
     * 현재 요청의 Locale 기반 메시지 조회
     */
    @Override
    public String get(String code, @Nullable Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return resolveMessage(code, args, locale);
    }

    /**
     * 지정된 Locale 기반 메시지 조회
     */
    @Override
    public String get(String code, Locale locale, @Nullable Object... args ) {
        return resolveMessage(code, args, locale);
    } 

    private String resolveMessage(String code, @Nullable Object[] args, Locale locale) {
        try {
            
            return messageSource.getMessage(code, args, locale); // fallback to code
        } catch (Exception e) {
            log.warn("Failed to resolve message: code<{}>, locale<{}>, error<{}>", code, locale, e.getMessage());
            return code;
        }
    }
}
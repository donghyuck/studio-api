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

import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.lang.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.service.I18n;

/**
 * I18n 인터페이스 구현체. Spring 의 MessageSource 기반으로 메시지를 로드.
 * 
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-21  donghyuck, son: 최초 생성.
 * 2025-08-13  donghyuck, son: MessageSourceAccessor 을 사용하도록 변경.
 *          </pre>
 */

@RequiredArgsConstructor
@Slf4j
public class I18nImpl implements I18n {

    private static final Object[] EMPTY_ARGS = new Object[0];
    private final MessageSourceAccessor accessor;

    /**
     * {@inheritDoc}
     */
    @Override
    public String get(String code, @Nullable Object[] args, Locale locale) {
        Object[] safeArgs = (args == null ? EMPTY_ARGS : args);
        return accessor.getMessage(code, safeArgs, locale);
    }

}
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
 * An interface for supporting internationalization (i18n).
 * <p>
 * This interface is designed to allow the application to provide different
 * messages based on various languages and regions.
 * 
 * @author donghyuck, son
 * @since 2025-08-13
 * @version 1.0
 */
@FunctionalInterface
public interface I18n {

    /**
     * Retrieves a message using the specified locale and arguments.
     *
     * @param code   the message code (non-null)
     * @param args   an array of arguments to be used for message formatting.
     *               <ul>
     *               <li>{@code null} is treated as no arguments</li>
     *               <li>an empty array is formatted as an empty value</li>
     *               </ul>
     * @param locale the target locale to look up (non-null)
     * @return the internationalized message
     * @throws org.springframework.context.NoSuchMessageException if the message
     *                                                            cannot be found
     */
    String get(String code, @Nullable Object[] args, Locale locale);

    /**
     * Retrieves a message based on the current thread's locale
     * (from {@link LocaleContextHolder}).
     *
     * @param code the message code (non-null)
     * @param args an array of message arguments. {@code null} is allowed.
     * @return the internationalized message
     */
    default String get(String code, @Nullable Object... args) {
        return get(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Returns the specified default message if the message cannot be found.
     *
     * @param code           the message code
     * @param defaultMessage the default message to return
     * @param args           an array of message arguments. {@code null} is allowed.
     * @return the internationalized message or the default message
     */
    default String getOrDefault(String code, String defaultMessage, @Nullable Object... args) {
        try {
            return get(code, args);
        } catch (Exception ex) {
            return defaultMessage;
        }
    }

    /**
     * Wraps the message in an {@link Optional}.
     *
     * @param code the message code
     * @param args an array of message arguments. {@code null} is allowed.
     * @return an {@link Optional} containing the message if it exists, otherwise
     *         {@link Optional#empty()}
     */
    default Optional<String> tryGet(String code, @Nullable Object... args) {
        try {
            return Optional.ofNullable(get(code, args));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves a message based on an explicit locale with a fallback to a default
     * message.
     *
     * @param code           the message code
     * @param args           an array of message arguments. {@code null} is allowed.
     * @param locale         the locale to look up
     * @param defaultMessage the default message
     * @return the message or the default message
     */
    default String getOrDefault(String code, @Nullable Object[] args, Locale locale, String defaultMessage) {
        try {
            return get(code, args, locale);
        } catch (Exception ex) {
            return defaultMessage;
        }
    }
    
}

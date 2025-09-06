package studio.echo.platform.util;

import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import lombok.NoArgsConstructor;
import studio.echo.platform.service.I18n;

/**
 * A utility class for working with the {@link I18n} interface.
 *
 * @author donghyuck, son
 * @since 2025-08-13
 * @version 1.0
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class I18nUtils {

    /** A fallback implementation that simply returns the message code. */
    public static final I18n NOOP = (code, args, locale) -> code;

    /**
     * Safely retrieves an {@link I18n} bean from an {@link ObjectProvider},
     * or returns the fallback {@link #NOOP} implementation if not available.
     *
     * @param provider the I18n ObjectProvider
     * @return an I18n implementation (either the bean or the fallback)
     */
    public static I18n resolve(ObjectProvider<I18n> provider) {
        return provider.getIfAvailable(() -> NOOP);
    }

    /**
     * Safely wraps a nullable {@link I18n} instance.
     *
     * @param i18n the I18n instance (nullable)
     * @return a non-null, safe I18n instance
     */
    public static I18n safe(I18n i18n) {
        return (i18n != null) ? i18n : NOOP;
    }

    /**
     * Safely gets a message from a nullable {@link I18n} instance.
     *
     * @param i18n the I18n instance (nullable)
     * @param code the message code
     * @param args the message arguments
     * @return the internationalized message, or the code if the i18n instance is
     *         null
     */
    public static String safeGet(@Nullable I18n i18n, String code, @Nullable Object... args) {
        return (i18n != null) ? i18n.get(code, args) : code;
    }

    /**
     * Safely gets a message from a nullable {@link I18n} instance with a specific
     * locale.
     *
     * @param i18n   the I18n instance (nullable)
     * @param code   the message code
     * @param locale the locale
     * @param args   the message arguments
     * @return the internationalized message, or the code if the i18n instance is
     *         null
     */
    public static String safeGet(@Nullable I18n i18n, String code, Locale locale, @Nullable Object... args) {
        return (i18n != null) ? i18n.get(code, args, locale) : code;
    }
}

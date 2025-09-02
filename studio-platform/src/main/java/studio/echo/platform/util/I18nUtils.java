package studio.echo.platform.util;

import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import lombok.NoArgsConstructor;
import studio.echo.platform.service.I18n;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class I18nUtils {

    /** 폴백 구현 (code 그대로 반환) */
    public static final I18n NOOP = (code, args, locale) -> code;

    /**
     * ObjectProvider에서 I18n 빈을 안전하게 가져오거나,
     * 없으면 폴백 {@link #NOOP}을 반환합니다.
     *
     * @param provider I18n ObjectProvider
     * @return I18n 구현체 (빈 또는 폴백)
     */
    public static I18n resolve(ObjectProvider<I18n> provider) {
        return provider.getIfAvailable(() -> NOOP);
    }

    /**
     * null 허용 I18n 인스턴스를 안전하게 감쌉니다.
     *
     * @param i18n I18n 인스턴스 (nullable)
     * @return 절대 null 이 아닌 안전한 I18n
     */
    public static I18n safe(I18n i18n) {
        return (i18n != null) ? i18n : NOOP;
    }

    public static String safeGet(@Nullable I18n i18n, String code, @Nullable Object... args) {
        return (i18n != null) ? i18n.get(code, args) : code;
    }

    public static String safeGet(@Nullable I18n i18n, String code, Locale locale, @Nullable Object... args) {
        return (i18n != null) ? i18n.get(code, locale, args) : code;
    }
}

package studio.echo.platform.service;

import java.util.Locale;

import org.springframework.lang.Nullable;

public interface I18n {

    String get(String code, @Nullable Object... args);

    String get(String code, Locale locale, @Nullable Object... args );

}

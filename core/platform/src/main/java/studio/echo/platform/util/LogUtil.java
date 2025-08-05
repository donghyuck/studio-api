package studio.echo.platform.util;

import lombok.NoArgsConstructor;
import studio.echo.platform.constant.Colors;
import studio.echo.platform.service.I18n;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class LogUtil {

    public static String format(I18n i18n, String code, Object... args) {
        return i18n.get(code, args);
    }

    public static String colored(Class<?> clazz, boolean simple, String color) {
        return Colors.format(color, simple ? clazz.getSimpleName() : clazz.getName());
    }

    public static String colored(String message, String color) {
        return Colors.format(color, message);
    }

    public static String blue(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.BLUE, simple ? clazz.getSimpleName() : clazz.getName());
    }

    public static String red(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.RED, simple ? clazz.getSimpleName() : clazz.getName());
    }

    public static String green(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.GREEN, simple ? clazz.getSimpleName() : clazz.getName());
    }

    public static String yellow(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.YELLOW, simple ? clazz.getSimpleName() : clazz.getName());
    }

    public static String blue(String message) {
        return Colors.format(Colors.BLUE, message );
    }

    public static String red(String message) {
        return Colors.format(Colors.RED, message );
    }

    public static String green(String message) {
        return Colors.format(Colors.GREEN, message );
    }

    public static String yellow(String message) {
        return Colors.format(Colors.YELLOW, message);
    }
}

package studio.echo.platform.util;

import lombok.NoArgsConstructor;
import studio.echo.platform.constant.Colors;
import studio.echo.platform.service.I18n;

/**
 * A utility class for logging-related operations.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class LogUtils {

    /**
     * Formats a log message using the given i18n instance.
     *
     * @param i18n the i18n instance
     * @param code the message code
     * @param args the message arguments
     * @return the formatted log message
     */
    public static String format(I18n i18n, String code, Object... args) {
        return i18n.get(code, args);
    }

    /**
     * Colors a class name with the specified color.
     *
     * @param clazz  the class
     * @param simple if {@code true}, use the simple name, otherwise use the full
     *               name
     * @param color  the color to apply
     * @return the colored class name
     */
    public static String colored(Class<?> clazz, boolean simple, String color) {
        return Colors.format(color, simple ? clazz.getSimpleName() : clazz.getName());
    }

    /**
     * Colors a message with the specified color.
     *
     * @param message the message to color
     * @param color   the color to apply
     * @return the colored message
     */
    public static String colored(String message, String color) {
        return Colors.format(color, message);
    }

    /**
     * Colors a class name blue.
     *
     * @param clazz  the class
     * @param simple if {@code true}, use the simple name, otherwise use the full
     *               name
     * @return the blue-colored class name
     */
    public static String blue(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.BLUE, simple ? clazz.getSimpleName() : clazz.getName());
    }

    /**
     * Colors a class name red.
     *
     * @param clazz  the class
     * @param simple if {@code true}, use the simple name, otherwise use the full
     *               name
     * @return the red-colored class name
     */
    public static String red(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.RED, simple ? clazz.getSimpleName() : clazz.getName());
    }

    /**
     * Colors a class name green.
     *
     * @param clazz  the class
     * @param simple if {@code true}, use the simple name, otherwise use the full
     *               name
     * @return the green-colored class name
     */
    public static String green(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.GREEN, simple ? clazz.getSimpleName() : clazz.getName());
    }

    /**
     * Colors a class name yellow.
     *
     * @param clazz  the class
     * @param simple if {@code true}, use the simple name, otherwise use the full
     *               name
     * @return the yellow-colored class name
     */
    public static String yellow(Class<?> clazz, boolean simple) {
        return Colors.format(Colors.YELLOW, simple ? clazz.getSimpleName() : clazz.getName());
    }

    /**
     * Colors a message blue.
     *
     * @param message the message to color
     * @return the blue-colored message
     */
    public static String blue(String message) {
        return Colors.format(Colors.BLUE, message );
    }

    /**
     * Colors a message red.
     *
     * @param message the message to color
     * @return the red-colored message
     */
    public static String red(String message) {
        return Colors.format(Colors.RED, message );
    }

    /**
     * Colors a message green.
     *
     * @param message the message to color
     * @return the green-colored message
     */
    public static String green(String message) {
        return Colors.format(Colors.GREEN, message );
    }

    /**
     * Colors a message yellow.
     *
     * @param message the message to color
     * @return the yellow-colored message
     */
    public static String yellow(String message) {
        return Colors.format(Colors.YELLOW, message);
    }
}

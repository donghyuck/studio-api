package studio.api.platform.i18n;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * * Localizer class for managing internationalization (i18n) resources.
 * This class provides methods to retrieve localized messages and version information
 * from a ResourceBundle.
 * It supports formatting messages with parameters and uses a specific ID format for message keys.
 * 
 * @author  donghyuck, son
 * @since 2025-07-08
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-08  donghyuck, son: 최초 생성.
 * </pre>
 */

public class Localizer {

    public static final String VERSION = "version";
    public static final String MESSAGE = "message";
    public static final DecimalFormat decimalFormat = new DecimalFormat("000000");

    private final ResourceBundle bundle;
    private final Locale locale;

    public Localizer(ResourceBundle bundle) {
        this(bundle, Locale.getDefault());
    }

    public Localizer(ResourceBundle bundle, Locale locale) {
        this.bundle = bundle;
        this.locale = locale;
    }

    public String getVersion() {
        return getString(VERSION);
    }

    public String getMessage(String id) {
        return getString(MESSAGE, id);
    }

    public String getMessage(int id) {
        return getMessage(decimalFormat.format(id));
    }

    public String format(String id, Object... args) {
        return MessageFormat.format(getMessage(id), args);
    }

    public String format(int id, Object... args) {
        return MessageFormat.format(getMessage(id), args);
    }

    public String getString(String prefix, String id) {
        try {
            return bundle.getString(prefix + id);
        } catch (MissingResourceException e) {
            return "[!" + prefix + id + "!]";
        }
    }

    public String getString(String key) {
        return getString("", key);
    }
}


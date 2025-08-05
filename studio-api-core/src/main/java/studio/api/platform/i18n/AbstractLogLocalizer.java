package studio.api.platform.i18n;

import java.util.ResourceBundle;

import lombok.extern.slf4j.Slf4j;

/**
 * AbstractLogLocalizer is a base class to simplify localized message access with fallback support.
 *
 * @author  donghyuck, son
 * @since 2025-07-07
 * @version 1.0 
 * 
 * <pre>
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ----------    --------    ---------------------------
 * *  2025-07-07    donghyuck    최초 생성
 * 
 * </pre>
 */

@Slf4j
public abstract class AbstractLogLocalizer {

    protected final Localizer localizer;

    protected AbstractLogLocalizer(String bundleName) {
        this.localizer = createLocalizer(bundleName);
    }

    private Localizer createLocalizer(String name) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(name);
            return new Localizer(bundle);
        } catch (Throwable t) {
            log.error("Failed to load resource bundle: {}", name, t);
            return null;
        }
    }

    protected String fallback(String id) {
        return "[Missing message: " + id + "]";
    }

    public String getMessage(String id) {
        return (localizer != null) ? localizer.getMessage(id) : fallback(id);
    }

    public String getMessage(int id) {
        return (localizer != null) ? localizer.getMessage(id) : fallback(String.valueOf(id));
    }

    public String format(String id, Object... args) {
        return (localizer != null) ? localizer.format(id, args) : fallback(id);
    }

    public String format(int id, Object... args) {
        return (localizer != null) ? localizer.format(id, args) : fallback(String.valueOf(id));
    }
}

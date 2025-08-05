package studio.api.platform.i18n;


import lombok.extern.slf4j.Slf4j;
/**
 * LogLocalizer class for managing internationalization (i18n) resources specifically for logging.
 * This class provides methods to retrieve localized messages related to logging operations.
 * It uses a ResourceBundle to load messages and supports both string and integer IDs for message retrieval.
 * If the ResourceBundle is not found or fails to load, it logs an error and returns a default message.
 * This class follows the Singleton pattern with lazy initialization to ensure that the Localizer instance
 * is created only when needed and is thread-safe.
 * It is designed to be used in logging contexts where localized messages are required for better user experience
 *  and maintainability.
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


@Slf4j
public final class LogLocalizer extends AbstractLogLocalizer {

    private static final LogLocalizer INSTANCE = new LogLocalizer();

    private LogLocalizer() {
        super(LogLocalizer.class.getName());
    }

    public static LogLocalizer getInstance() {
        return INSTANCE;
    }
}
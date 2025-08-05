package studio.api.platform.i18n;

import lombok.extern.slf4j.Slf4j;

/**
 * PlatformLogLocalizer class for managing internationalization (i18n) resources specifically for platform logging.
 * This class provides methods to retrieve localized messages related to platform components and their states.
 * It uses a ResourceBundle to load messages and supports both string and integer IDs for message retrieval.
 * If the ResourceBundle is not found or fails to load, it logs an error and returns a default message.
 * This class follows the Singleton pattern with lazy initialization to ensure that the Localizer instance
 * is created only when needed and is thread-safe.
 * It is designed to be used in platform logging contexts where localized messages are required for better user experience.
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
public final class PlatformLogLocalizer extends AbstractLogLocalizer {

    private static final PlatformLogLocalizer INSTANCE = new PlatformLogLocalizer();

    private PlatformLogLocalizer() {
        super(PlatformLogLocalizer.class.getName());
    }

    public static PlatformLogLocalizer getInstance() {
        return INSTANCE;
    }

    public enum MessageCode {

        COMPONENT_STATE("002001"),
        COMPONENT_STATE_CAHNGE("002002"),
        COMPONENT_MESSAGE_ONE("002003"),
        COMPONENT_MESSAGE_TWO("002004"),
        COMPONENT_MESSAGE_THREE("002005"),
        COMPONENT_MESSAGE_FOUR("002006"),
        COMPONENT_MESSAGE_CTOR("002007"),
        COMPONENT_DISABLED("002011"),
        COMPONENT_INITIALIZED_WITH_ERRORS("002012"),
        CUSTOM_COMPONENT_STATE("002020"),
        DATABASE_CONNECTION_FAIL("002150"),
        SERVICE_STATE("002030");

        private final String code;

        MessageCode(String code) {
            this.code = code;
        }

        public String code() {
            return this.code;
        }

        @Override
        public String toString() {
            return code;
        }        
    }
}

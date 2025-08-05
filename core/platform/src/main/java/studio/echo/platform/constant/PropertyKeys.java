package studio.echo.platform.constant;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PropertyKeys {

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Main {
        public static final String HOME = "studio.home";
        public static final String SHOW_BANNER = "studio.banner-mode";
        public static final String LOG_ENVIRONMENT = "studio.env.log.enabled";
        public static final String LOG_ENVIRONMENT_VALUES = "studio.env.log.print-values";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Components {
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String ENABLED = "application.components.application-properties.enabled";
            public static final String PERSISTENCE_MYBATIS = "application.components.application-properties.persistence.mybatis";
        }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Features {

        public static final String PREFIX = "studio.features";

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String ENABLED = PREFIX + ".application-properties.enabled";
        }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Persistence {
        public static final String PREFIX = "studio.persistence";
        public static final String JPA = PREFIX+".jpa";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class I18n {
        public static final String PREFIX = "studio.i18n";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Security {
        public static final String PREFIX = "studio.security";
    }

}

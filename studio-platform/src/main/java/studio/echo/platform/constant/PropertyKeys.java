package studio.echo.platform.constant;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PropertyKeys {

    private static final String ENABLED_VALUE_STRING = ".enabled";
    private static final String FAIL_IF_MISSING_VALUE_STRING = ".fail-if-missing";

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Main {
        public static final String PREFIX = "studio";
        public static final String HOME = PREFIX + ".home";
        public static final String SHOW_BANNER = PREFIX + ".banner-mode";
        public static final String LOG_ENVIRONMENT = PREFIX + ".env.log.enabled";
        public static final String LOG_ENVIRONMENT_VALUES = PREFIX + ".env.log.print-values";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Jpa {
        public static final String PREFIX = Main.PREFIX + ".jpa";
        public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Auditing {
                public static final String PREFIX = Jpa.PREFIX + ".auditing";
                public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class I18n {
        public static final String PREFIX = Main.PREFIX + ".i18n";
        public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
        public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Security {
        public static final String PREFIX = Main.PREFIX + ".security";
        public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
        public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jwt {

            public static final String PREFIX = Security.PREFIX + ".jwt";

            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Endpoints {
                public static final String PREFIX = Jwt.PREFIX + ".endpoints";
                public static final String BASE_PATH = PREFIX + ".base-path";
            }
        }   

    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Components {
        public static final String PREFIX = Main.PREFIX + ".components";

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String PREFIX = Components.PREFIX + ".application-properties";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jasypt {
            public static final String PREFIX = Components.PREFIX + ".jasypt";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
            public static final String HTTP = PREFIX + ".http";
            public static final String ENCRYPTOR = PREFIX + ".encryptor";
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class User {
            public static final String PREFIX = Components.PREFIX + ".user";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Features {
        public static final String PREFIX = Main.PREFIX + ".features";

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String PREFIX = Features.PREFIX + ".application-properties";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jasypt {
            public static final String PREFIX = Features.PREFIX + ".jasypt";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
            public static final String ENCRYPTOR = PREFIX + ".encryptor";
            public static final String HTTP = PREFIX + ".http";
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class User {
            public static final String PREFIX = Features.PREFIX + ".user";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;

            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Web {
                public static final String PREFIX = Features.User.PREFIX + ".web";
                public static final String BASE_PATH = PREFIX + ".base-path"; 
                public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING; 

                @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
                public static final class Self {
                    public static final String PREFIX = Features.User.Web.PREFIX + ".self"; 
                    public static final String PATH = PREFIX + ".path";
                }
                @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
                public static final class Endpoints {
                    public static final String PREFIX = Features.User.Web.PREFIX + ".endpoints"; 

                }
            }

        }
    }

}

package studio.one.platform.constant;

import lombok.NoArgsConstructor;

/**
 * A utility class that contains constants for configuration property keys.
 * This class cannot be instantiated and contains only static inner classes
 * for different configuration sections.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PropertyKeys {

    private static final String ENABLED_VALUE_STRING = ".enabled";
    private static final String FAIL_IF_MISSING_VALUE_STRING = ".fail-if-missing"; 
    private static final String PERSISTENCE_STRING = ".persistence";
    private static final String TYPE_VALUE_STRING = ".type";


    /**
     * Contains main application property keys.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Main {
        public static final String PREFIX = "studio";
        public static final String HOME = PREFIX + ".home";
        public static final String SHOW_BANNER = PREFIX + ".banner-mode";
        public static final String LOG_ENVIRONMENT = PREFIX + ".env.log.enabled";
        public static final String LOG_ENVIRONMENT_VALUES = PREFIX + ".env.log.print-values";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Persistence {
        public static final String PREFIX = Main.PREFIX + PERSISTENCE_STRING;
        public static final String TYPE = PREFIX + TYPE_VALUE_STRING;

        /**
         * Contains JPA related property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jpa {
            public static final String PREFIX = Persistence.PREFIX + ".jpa";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;

            /**
             * Contains JPA auditing property keys.
             */
            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Auditing {
                public static final String PREFIX = Jpa.PREFIX + ".auditing";
                public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            }
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class SecurityAcl {
            public static final String PREFIX = Features.PREFIX + ".security-acl";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;

            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Entity {
                public static final String PREFIX = SecurityAcl.PREFIX + ".entity-packages";
            }

            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Repository {
                public static final String PREFIX = SecurityAcl.PREFIX + ".repository-packages";
            }
            
            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Admin {
                public static final String PREFIX = SecurityAcl.PREFIX + ".admin";
                public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
                public static final String BASE_PATH = PREFIX + ".base-path";
            }
        }
    }

    /**
     * Contains i18n related property keys.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class I18n {
        public static final String PREFIX = Main.PREFIX + ".i18n";
        public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
        public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
    }

    /**
     * Contains security related property keys.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Security {
        public static final String PREFIX = Main.PREFIX + ".security";
        public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
        public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Audit {
            public static final String PREFIX = Security.PREFIX + ".audit";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String LOGIN_FAILURE = PREFIX + ".login-failure";
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Acl {
            public static final String PREFIX = Security.PREFIX + ".acl";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String ENTITY_PACKAGES = PREFIX + ".entity-packages";
            public static final String REPOSITORY_PACKAGES = PREFIX + ".repository-packages";
            public static final String PERSISTENCE = PREFIX + PERSISTENCE_STRING;
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Auth {
            public static final String PREFIX = Security.PREFIX + ".auth";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String LOCK = PREFIX + ".lock";
        }

        /**
         * Contains JWT related property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jwt {

            public static final String PREFIX = Security.PREFIX + ".jwt";
            public static final String REFRESH_COOKIE_NAME = PREFIX + ".refresh-cookie-name";

            /**
             * Contains JWT endpoint property keys.
             */
            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Endpoints {
                public static final String PREFIX = Jwt.PREFIX + ".endpoints";
                public static final String BASE_PATH = PREFIX + ".base-path";
            }
        }

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Cors {
            public static final String PREFIX = Security.PREFIX + ".cors";
        }

    }

    /**
     * Contains component related property keys.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Components {
        public static final String PREFIX = Main.PREFIX + ".components";

        /**
         * Contains application properties component property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String PREFIX = Components.PREFIX + ".application-properties";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        }

        /**
         * Contains Jasypt component property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jasypt {
            public static final String PREFIX = Components.PREFIX + ".jasypt";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
            public static final String HTTP = PREFIX + ".http";
            public static final String ENCRYPTOR = PREFIX + ".encryptor";
        }

        /**
         * Contains user component property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class User {
            public static final String PREFIX = Components.PREFIX + ".user";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
        }
    }

    /**
     * Contains feature related property keys.
     */
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Features {
        public static final String PREFIX = Main.PREFIX + ".features";

        /**
         * Contains application properties feature property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class ApplicationProperties {
            public static final String PREFIX = Features.PREFIX + ".application-properties";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
            public static final String PERSISTENCE = PREFIX + PERSISTENCE_STRING;
        }

        /**
         * Contains Jasypt feature property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Jasypt {
            public static final String PREFIX = Features.PREFIX + ".jasypt";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;
            public static final String ENCRYPTOR = PREFIX + ".encryptor";
            public static final String HTTP = PREFIX + ".http";
        }

        /**
         * Contains user feature property keys.
         */
        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class User {
            public static final String PREFIX = Features.PREFIX + ".user";
            public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;
            public static final String FAIL_IF_MISSING = PREFIX + FAIL_IF_MISSING_VALUE_STRING;

            /**
             * Contains user web feature property keys.
             */
            @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
            public static final class Web {
                public static final String PREFIX = Features.User.PREFIX + ".web";
                public static final String BASE_PATH = PREFIX + ".base-path";
                public static final String ENABLED = PREFIX + ENABLED_VALUE_STRING;

                /**
                 * Contains user self-service web feature property keys.
                 */
                @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
                public static final class Self {
                    public static final String PREFIX = Features.User.Web.PREFIX + ".self";
                    public static final String PATH = PREFIX + ".path";
                }

                /**
                 * Contains user web endpoint feature property keys.
                 */
                @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
                public static final class Endpoints {
                    public static final String PREFIX = Features.User.Web.PREFIX + ".endpoints";
                }
            }

        }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Cloud {
        public static final String PREFIX = Main.PREFIX + ".cloud";

    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Ai {
        public static final String PREFIX = Main.PREFIX + ".ai";
    }    
}

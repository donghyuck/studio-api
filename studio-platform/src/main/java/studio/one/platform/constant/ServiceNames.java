/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ServiceNames.java
 *      @date 2025
 *
 */
package studio.one.platform.constant;

import lombok.NoArgsConstructor;

/**
 * A utility class that contains constants for Spring bean names (qualifiers).
 * This class cannot be instantiated and contains only static members.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ServiceNames {

    public static final String PREFIX = "components";

    public static final String APPLICATION_PROPERTIES = PREFIX + ":foundation:application-properties"; 
    public static final String REPOSITORY = PREFIX + ":foundation:repository"; 
    public static final String I18N = PREFIX + ":foundation:i18n";

    public static final String JDBC_TEMPLATE = PREFIX + ":data:jdbc-template";
    public static final String NAMED_JDBC_TEMPLATE = PREFIX + ":data:named-jdbc-template";
    public static final String SQL_QUERY = PREFIX + ":data:sql-query";
    public static final String SQL_QUERY_CONFIGURATION = SQL_QUERY + ":configuration";
    public static final String SQL_QUERY_FACTORY = SQL_QUERY + ":factory";
    public static final String SQL_QUERY_CLIENT = SQL_QUERY + ":client";
    public static final String SQL_QUERY_DEPLOY= SQL_QUERY + ":deploy";



    public static final String I18N_MESSAGE_SOURCE = I18N + ":message-source";
    public static final String I18N_MESSAGE_ACCESSOR = I18N + ":message-accessor";

    public static final String JASYPT_MESSAGE_ACCESSOR = PREFIX + ":jasypt:message-accessor";
    public static final String JASYPT_MESSAGE_SOURCE = PREFIX + ":jasypt:message-source";

    public static final String AUTHENTICATION_MANAGER = PREFIX + ":security:authentication-manager";

    public static final String USER_DETAILS_SERVICE = PREFIX + ":security:user-details-service";

    public static final String PASSWORD_ENCODER = PREFIX + ":security:password-encoder";

    public static final String JWT_TOKEN_PROVIDER = PREFIX + ":security:jwt-token-provider";

    public static final String CORS_CONFIGURATION_SOURCE = PREFIX + ":security:cors-configuration-source";

    public static final String DOMAIN_POLICY_REGISTRY = PREFIX + ":security:domain-policy-registry";

    public static final String DOMAIN_ENDPOINT_AUTHZ = "endpointAuthz";

    public static final String SECURITY_AUDIT_LOGIN_FAILURE_LOG_RETENTION_JOB = PREFIX + ":security:audit:login-failure-log-retention-job";
    public static final String SECURITY_AUDIT_LOGIN_FAILURE_EVENT_LISTENER = PREFIX + ":security:audit:login-failure-event-listener"; 
    public static final String SECURITY_AUDIT_LOGIN_FAILURE_EVENT_PUBLISHER = PREFIX + ":security:audit:login-failure-event-publisher";
    public static final String SECURITY_AUDIT_LOGIN_FAILURE_EXECUTOR = PREFIX + ":security:audit:login-failure-executor"; 
    public static final String SECURITY_AUDIT_LOGIN_SUCCESS_EVENT_LISTENER = PREFIX + ":security:audit:login-success-event-listener";
    public static final String SECURITY_ACCOUNT_LOCK_SERVICE = PREFIX + ":security:account-lock-service";

    public static final String CLOUD_PREFIX = PREFIX + ":cloud";

}

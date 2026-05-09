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
 *      @file LoginFailureAuditFields.java
 *      @date 2026
 *
 */

package studio.one.base.security.audit;

public final class LoginFailureAuditFields {

    public static final int USERNAME_MAX_LENGTH = 150;
    public static final int USER_AGENT_MAX_LENGTH = 512;
    public static final int FAILURE_TYPE_MAX_LENGTH = 128;
    public static final int MESSAGE_MAX_LENGTH = 1000;

    private LoginFailureAuditFields() {
    }

    public static String username(String value) {
        return truncate(value, USERNAME_MAX_LENGTH, "");
    }

    public static String userAgent(String value) {
        return truncate(value, USER_AGENT_MAX_LENGTH, null);
    }

    public static String failureType(String value) {
        return truncate(value, FAILURE_TYPE_MAX_LENGTH, null);
    }

    public static String message(String value) {
        return truncate(value, MESSAGE_MAX_LENGTH, null);
    }

    private static String truncate(String value, int maxLength, String fallback) {
        if (value == null) {
            return fallback;
        }
        String sanitized = stripControlCharacters(value);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }

    private static String stripControlCharacters(String value) {
        StringBuilder sanitized = null;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            char replacement = Character.isISOControl(ch) ? ' ' : ch;
            if (sanitized != null) {
                sanitized.append(replacement);
            } else if (replacement != ch) {
                sanitized = new StringBuilder(value.length());
                sanitized.append(value, 0, index);
                sanitized.append(replacement);
            }
        }
        if (sanitized == null) {
            return value;
        }
        return sanitized.toString();
    }
}

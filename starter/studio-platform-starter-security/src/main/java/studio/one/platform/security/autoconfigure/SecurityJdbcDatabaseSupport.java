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
 *      @file SecurityJdbcDatabaseSupport.java
 *      @date 2026
 *
 */

package studio.one.platform.security.autoconfigure;

import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.autoconfigure.jdbc.JdbcDatabaseSupport;

final class SecurityJdbcDatabaseSupport {

    private SecurityJdbcDatabaseSupport() {
    }

    static void requirePostgreSQL(NamedParameterJdbcTemplate template, String featureName) {
        JdbcDatabaseSupport.requirePostgreSQL(template, "Security " + featureName);
    }

    static void requirePostgreSQL(DataSource dataSource, String featureName) {
        JdbcDatabaseSupport.requirePostgreSQL(dataSource, "Security " + featureName);
    }
}

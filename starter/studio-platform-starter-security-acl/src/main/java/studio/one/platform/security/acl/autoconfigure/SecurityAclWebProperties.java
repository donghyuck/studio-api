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
 *      @file SecurityAclWebProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Web configuration for exposing ACL sync endpoints.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "studio.security.acl.web")
public class SecurityAclWebProperties {

    /**
     * Enables the web endpoints that allow manual ACL synchronization.
     */
    private boolean enabled = false;

    /**
     * Base path where the controller is exposed.
     */
    private String basePath = "/api/mgmt";
}

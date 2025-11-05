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
 *      @file AclProperties.java
 *      @date 2025
 *
 */


package studio.one.platform.security.authz;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
/**
 * Properties for configuring ACL (Access Control List) web authorization
 * policies for different domains and components.
 *
 * <p>Example YAML configuration:</p>
 * <pre>{@code
 * echo:
 *   security:
 *     acl:
 *       domains:
 *         group:
 *           roles:
 *             read:  [ADMIN, MANAGER]
 *             write: [ADMIN]
 *           components:
 *             member:
 *               roles:
 *                 read:  [ADMIN, MANAGER]
 *                 write: [ADMIN]
 * }</pre>
 * 
 * @author  donghyuck, son
 * @since 2025-09-01
 * @version 1.0
 */
@Getter
@Setter
public class AclProperties {  
    
    private Map<String, DomainPolicy> domains = new HashMap<>();

    /**
     * Represents the policy for a specific domain.
     */
    @Getter
    @Setter
    public static class DomainPolicy { 
        private Roles roles = new Roles();
        private Map<String, ComponentPolicy> components = new HashMap<>();
    }

    /**
     * Represents the policy for a specific component within a domain.
     */
    @Getter
    @Setter
    public static class ComponentPolicy { 
        private Roles roles;  
    }

    /**
     * Represents the roles required for different types of access.
     */
    @Getter
    @Setter
    public static class Roles {
        private List<String> read = Collections.emptyList();
        private List<String> write = Collections.emptyList();
        private List<String> admin = Collections.emptyList();
    }
}

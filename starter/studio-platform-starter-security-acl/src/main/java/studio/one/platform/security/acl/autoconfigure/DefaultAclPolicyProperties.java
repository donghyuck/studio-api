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
 *      @file DefaultAclPolicyProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;
import studio.one.base.security.acl.policy.AclPolicyDescriptor;

/**
 * Holds default ACL policies that should be seeded into the database on startup.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "studio.security.acl.defaults")
public class DefaultAclPolicyProperties {

    /** Enables default policy seeding. */
    private boolean enabled = false;

    /** ACL policy descriptors that can be synchronized on demand. */
    private List<AclPolicyDescriptor> policies = new ArrayList<>();
}

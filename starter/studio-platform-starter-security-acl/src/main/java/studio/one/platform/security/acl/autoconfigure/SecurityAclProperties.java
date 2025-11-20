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
 *      @file SecurityAclProperties.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

/**
 * Configuration properties for the database-backed ACL policy contributor.
 */
@ConfigurationProperties(prefix = PropertyKeys.Security.Acl.PREFIX)
@Validated
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SecurityAclProperties extends FeatureToggle {

    public static final String DEFAULT_ENTITY_PACKAGE = "studio.one.base.security.acl.domain.entity";
    public static final String DEFAULT_REPOSITORY_PACKAGE = "studio.one.base.security.acl.persistence";


    /**
     * JPA entity packages to scan.
     */
    @NotEmpty
    private List<String> entityPackages = List.of(DEFAULT_ENTITY_PACKAGE);

    /**
     * JPA repository packages to scan.
     */
    @NotEmpty
    private List<String> repositoryPackages = List.of(DEFAULT_REPOSITORY_PACKAGE);

    /**
     * Optional aliases that map normalized domains (e.g. "users") to logical keys
     * (e.g. "user-management").
     */
    private Map<String, String> domainAliases = Map.of();

    /**
     * Values that should be treated as "root" identifiers rather than component
     * names.
     */
    private Set<String> domainIndicators = Set.of("*", "__domain__", "__root__");
}

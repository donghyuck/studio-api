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

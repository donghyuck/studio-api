package studio.one.platform.security.acl.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

/**
 * Properties for the optional ACL administration endpoints.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = PropertyKeys.Security.Acl.PREFIX + ".web")
public class SecurityAclAdminProperties {

    /**
     * Enables the admin endpoints.
     */
    private boolean enabled = false;

    /**
     * Base path for the admin API.
     */
    private String basePath = "/api/mgmt";
}

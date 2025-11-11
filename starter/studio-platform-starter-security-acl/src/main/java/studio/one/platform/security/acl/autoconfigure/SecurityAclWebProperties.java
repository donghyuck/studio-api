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

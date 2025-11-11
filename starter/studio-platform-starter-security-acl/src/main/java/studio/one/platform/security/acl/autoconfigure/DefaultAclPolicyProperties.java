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

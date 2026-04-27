package studio.one.platform.autoconfigure.perisitence.jpa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import studio.one.platform.autoconfigure.JpaAuditingProperties;
import studio.one.platform.constant.PropertyKeys;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jpa.JpaAuditingAutoConfiguration} instead.
 */
@Configuration
@EnableConfigurationProperties(JpaAuditingProperties.class)
@ConditionalOnClass(EnableJpaAuditing.class)
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jpa.Auditing.PREFIX, name = "enabled", havingValue = "true")
@Deprecated(forRemoval = false)
public class JpaAuditingAutoConfiguration extends studio.one.platform.autoconfigure.persistence.jpa.JpaAuditingAutoConfiguration {
}

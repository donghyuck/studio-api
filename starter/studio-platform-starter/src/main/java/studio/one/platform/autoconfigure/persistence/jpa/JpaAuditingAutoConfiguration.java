package studio.one.platform.autoconfigure.persistence.jpa;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import studio.one.platform.autoconfigure.JpaAuditingProperties;
import studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAware;
import studio.one.platform.autoconfigure.persistence.jpa.auditor.FixedAuditorAware;
import studio.one.platform.autoconfigure.persistence.jpa.auditor.HeaderAuditorAware;
import studio.one.platform.autoconfigure.persistence.jpa.auditor.SecurityAuditorAware;
import studio.one.platform.constant.PropertyKeys;

@Configuration
@EnableConfigurationProperties(JpaAuditingProperties.class)
@ConditionalOnClass(EnableJpaAuditing.class)
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jpa.Auditing.PREFIX, name = "enabled", havingValue = "true")
public class JpaAuditingAutoConfiguration {

    @Configuration
    @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider", auditorAwareRef = "auditorAware")
    static class EnableConfig {
        
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock auditingClock(JpaAuditingProperties props) {
        return Clock.system(ZoneId.of(props.getClock().getZoneId()));
    }

    @Bean(name = "auditingDateTimeProvider")
    @ConditionalOnMissingBean(name = "auditingDateTimeProvider")
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(java.time.OffsetDateTime.now(clock));
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditorAware<String> auditorAware(
            JpaAuditingProperties props,
            ObjectProvider<AuditorAware<String>> externalAuditorAwares) {
        switch (props.getAuditor().getStrategy().toLowerCase()) {
            case "fixed":
                return new FixedAuditorAware(props.getAuditor().getFixed());
            case "header":
                return new HeaderAuditorAware(props.getAuditor().getHeader());
            case "composite":
                return new CompositeAuditorAware(resolveExternalAuditors(externalAuditorAwares), props);
            case "security":
            default:
                return new SecurityAuditorAware();
        }
    }

    private List<AuditorAware<String>> resolveExternalAuditors(ObjectProvider<AuditorAware<String>> provider) {
        return provider.orderedStream()
                .filter(this::isExternalAuditorAware)
                .toList();
    }

    private boolean isExternalAuditorAware(AuditorAware<String> auditorAware) {
        String className = auditorAware.getClass().getName();
        return !className.startsWith("studio.one.platform.autoconfigure.persistence.jpa.auditor.")
                && !className.startsWith("studio.one.platform.autoconfigure.perisistence.jpa.auditor.");
    }

}

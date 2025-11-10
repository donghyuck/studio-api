package studio.one.platform.autoconfigure.perisitence.jpa;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

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
import studio.one.platform.autoconfigure.perisistence.jpa.auditor.CompositeAuditorAware;
import studio.one.platform.autoconfigure.perisistence.jpa.auditor.FixedAuditorAware;
import studio.one.platform.autoconfigure.perisistence.jpa.auditor.HeaderAuditorAware;
import studio.one.platform.autoconfigure.perisistence.jpa.auditor.SecurityAuditorAware;
import studio.one.platform.constant.PropertyKeys;

@Configuration
@EnableConfigurationProperties(JpaAuditingProperties.class)
@ConditionalOnClass(EnableJpaAuditing.class)
@ConditionalOnProperty(prefix = PropertyKeys.Persistence.Jpa.Auditing.PREFIX, name = "enabled", havingValue = "true")
public class PlatformJpaAuditingAutoConfiguration {

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
    public AuditorAware<String> auditorAware(JpaAuditingProperties props) {
        switch (props.getAuditor().getStrategy().toLowerCase()) {
            case "fixed":
                return new FixedAuditorAware(props.getAuditor().getFixed());
            case "header":
                return new HeaderAuditorAware(props.getAuditor().getHeader());
            case "composite":
                return new CompositeAuditorAware(props);
            case "security":
            default:
                return new SecurityAuditorAware();
        }
    }

}

package studio.one.platform.autoconfigure.persistence.jpa.auditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.autoconfigure.JpaAuditingProperties;
import org.springframework.data.domain.AuditorAware;

class CompositeAuditorAwareTest {

    @Test
    void returnsFirstCustomAuditorBeforeDefaultDelegates() {
        JpaAuditingProperties props = new JpaAuditingProperties();
        props.getAuditor().setComposite(List.of("fixed"));
        props.getAuditor().setFixed("default-user");

        CompositeAuditorAware aware = new CompositeAuditorAware(
                List.<AuditorAware<String>>of(() -> Optional.of("custom-user")),
                props);

        assertThat(aware.getCurrentAuditor()).contains("custom-user");
    }

    @Test
    void preservesDefaultFallbackWhenCustomDelegatesAreEmpty() {
        JpaAuditingProperties props = new JpaAuditingProperties();
        props.getAuditor().setComposite(List.of("fixed"));
        props.getAuditor().setFixed("default-user");

        CompositeAuditorAware aware = new CompositeAuditorAware(
                List.<AuditorAware<String>>of(() -> Optional.empty()),
                props);

        assertThat(aware.getCurrentAuditor()).contains("default-user");
    }

    @Test
    void fallsBackToSystemWhenNoDelegateProvidesAuditor() {
        JpaAuditingProperties props = new JpaAuditingProperties();
        props.getAuditor().setComposite(List.of());

        CompositeAuditorAware aware = new CompositeAuditorAware(props);

        assertThat(aware.getCurrentAuditor()).contains("system");
    }
}

package studio.one.platform.autoconfigure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.AuditorAware;

import studio.one.platform.autoconfigure.JpaAuditingProperties;

class JpaAuditingAutoConfigurationTest {

    @Test
    void compositeStrategyIncludesExternalAuditorAwareDelegates() {
        JpaAuditingProperties props = new JpaAuditingProperties();
        props.getAuditor().setStrategy("composite");
        props.getAuditor().setComposite(java.util.List.of("fixed"));
        props.getAuditor().setFixed("default-user");

        AuditorAware<String> auditorAware = new JpaAuditingAutoConfiguration().auditorAware(
                props,
                new SingleAuditorAwareProvider(() -> Optional.of("custom-user")));

        assertThat(auditorAware.getCurrentAuditor()).contains("custom-user");
    }

    private static final class SingleAuditorAwareProvider implements ObjectProvider<AuditorAware<String>> {

        private final AuditorAware<String> value;

        private SingleAuditorAwareProvider(AuditorAware<String> value) {
            this.value = value;
        }

        @Override
        public AuditorAware<String> getObject(Object... args) {
            return value;
        }

        @Override
        public AuditorAware<String> getIfAvailable() {
            return value;
        }

        @Override
        public AuditorAware<String> getIfUnique() {
            return value;
        }

        @Override
        public AuditorAware<String> getObject() {
            return value;
        }

        @Override
        public Stream<AuditorAware<String>> stream() {
            return Stream.of(value);
        }

        @Override
        public Stream<AuditorAware<String>> orderedStream() {
            return stream();
        }
    }
}

package studio.one.platform.autoconfigure.perisitence.jpa.auditor;

import java.util.Optional;

public class FixedAuditorAware implements org.springframework.data.domain.AuditorAware<String> {
    private final String fixed;

    public FixedAuditorAware(String fixed) {
        this.fixed = fixed;
    }

    @Override
    public java.util.Optional<String> getCurrentAuditor() {
        return Optional.of(fixed);
    }
}

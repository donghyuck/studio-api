package studio.one.platform.autoconfigure.perisistence.jpa.auditor;

import java.util.List;

import studio.one.platform.autoconfigure.JpaAuditingProperties;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAware} instead.
 */
@Deprecated(forRemoval = false)
public class CompositeAuditorAware extends studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAware {

    public CompositeAuditorAware(JpaAuditingProperties props) {
        super(props);
    }

    public CompositeAuditorAware(List<org.springframework.data.domain.AuditorAware<String>> delegates,
            JpaAuditingProperties props) {
        super(delegates, props);
    }
}

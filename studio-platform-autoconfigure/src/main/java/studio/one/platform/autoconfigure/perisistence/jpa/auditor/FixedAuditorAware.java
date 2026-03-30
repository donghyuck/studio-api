package studio.one.platform.autoconfigure.perisistence.jpa.auditor;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jpa.auditor.FixedAuditorAware} instead.
 */
@Deprecated(forRemoval = false)
public class FixedAuditorAware extends studio.one.platform.autoconfigure.persistence.jpa.auditor.FixedAuditorAware {

    public FixedAuditorAware(String fixed) {
        super(fixed);
    }
}

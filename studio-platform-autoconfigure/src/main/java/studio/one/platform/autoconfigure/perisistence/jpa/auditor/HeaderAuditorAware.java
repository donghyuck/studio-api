package studio.one.platform.autoconfigure.perisistence.jpa.auditor;

/**
 * @deprecated Use {@link studio.one.platform.autoconfigure.persistence.jpa.auditor.HeaderAuditorAware} instead.
 */
@Deprecated(forRemoval = false)
public class HeaderAuditorAware extends studio.one.platform.autoconfigure.persistence.jpa.auditor.HeaderAuditorAware {

    public HeaderAuditorAware(String headerName) {
        super(headerName);
    }
}

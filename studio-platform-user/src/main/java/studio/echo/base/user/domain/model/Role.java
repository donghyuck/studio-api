package studio.echo.base.user.domain.model;

import java.time.Instant;

public interface Role {

    public abstract Long getRoleId();

    public abstract String getName();

    public abstract String getDescription();

    public Instant getCreationDate();

    public Instant getModifiedDate();
}

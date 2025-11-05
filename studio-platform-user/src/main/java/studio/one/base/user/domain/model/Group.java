package studio.one.base.user.domain.model;

import java.time.Instant;

import studio.one.platform.domain.model.PropertyAware;

public interface Group extends PropertyAware{

    public abstract Long getGroupId();

    public abstract String getName();

    public abstract String getDescription();

    public abstract Long getMemberCount();

    public Instant getCreationDate();

    public Instant getModifiedDate();

}

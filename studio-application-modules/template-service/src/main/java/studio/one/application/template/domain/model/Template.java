package studio.one.application.template.domain.model;

import java.time.Instant;

import studio.one.platform.domain.model.PropertyAware;
import studio.one.platform.domain.model.TypeObject;

public interface Template extends PropertyAware, TypeObject {

    public long getTemplateId();

    public void setTemplateId(long templateId);

    public String getName();

    public void setName(String name);

    public String getDisplayName();

    public void setDisplayName(String displayName);

    public String getDescription();

    public void setDescription(String description);

    public long getCreatedBy();

    public void setCreatedBy(long userId);

    public long getUpdatedBy();

    public void setUpdatedBy(long userId);

    public void setObjectType(int objectType);

    public void setObjectId(long objectId);

    public Instant getCreatedAt();

    public Instant getUpdatedAt();

    public void setCreatedAt(Instant at);

    public void setUpdatedAt(Instant at);

    public String getSubject();

    public void setSubject(String subject);

    public String getBody();

    public void setBody(String body);

}

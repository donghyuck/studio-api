package studio.one.application.template.domain.model;

import java.time.Instant;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DefaultTemplate implements Template {

    private int objectType;

    private long objectId;

    private long templateId;

    private String name;

    private String displayName;

    private String description;

    private long createdBy;

    private long updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    private String subject;

    private String body;

    private Map<String, String> properties;

}

package studio.one.application.template.web.dto;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Value;
import studio.one.application.template.domain.model.Template;

@Value
@Builder
public class TemplateDto {
    long templateId;
    int objectType;
    long objectId;
    String name;
    String displayName;
    String description;
    String subject;
    String body;
    long createdBy;
    long updatedBy;
    Instant createdAt;
    Instant updatedAt;
    Map<String, String> properties;

    public static TemplateDto from(Template template) {
        return TemplateDto.builder()
                .templateId(template.getTemplateId())
                .objectType(template.getObjectType())
                .objectId(template.getObjectId())
                .name(template.getName())
                .displayName(template.getDisplayName())
                .description(template.getDescription())
                .subject(template.getSubject())
                .body(template.getBody())
                .createdBy(template.getCreatedBy())
                .updatedBy(template.getUpdatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .properties(template.getProperties())
                .build();
    }
}

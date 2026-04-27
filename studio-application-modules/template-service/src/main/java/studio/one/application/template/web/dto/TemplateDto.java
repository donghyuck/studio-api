package studio.one.application.template.web.dto;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Value;
import studio.one.application.template.domain.model.Template;
import studio.one.platform.identity.UserDto;

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
    UserDto createdBy;
    UserDto updatedBy;
    Instant createdAt;
    Instant updatedAt;
    Map<String, String> properties;

    public static TemplateDto summary(Template template, UserDto createdBy, UserDto updatedBy) {
        return TemplateDto.builder()
                .templateId(template.getTemplateId())
                .objectType(template.getObjectType())
                .objectId(template.getObjectId())
                .name(template.getName())
                .displayName(template.getDisplayName())
                .description(template.getDescription())
                .subject(template.getSubject())
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    public static TemplateDto from(Template template, UserDto createdBy, UserDto updatedBy) {
        return TemplateDto.builder()
                .templateId(template.getTemplateId())
                .objectType(template.getObjectType())
                .objectId(template.getObjectId())
                .name(template.getName())
                .displayName(template.getDisplayName())
                .description(template.getDescription())
                .subject(template.getSubject())
                .body(template.getBody())
                .createdBy(createdBy)
                .updatedBy(updatedBy)
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .properties(template.getProperties())
                .build();
    }
}

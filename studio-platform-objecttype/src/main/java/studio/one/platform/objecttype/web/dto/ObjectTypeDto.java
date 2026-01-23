package studio.one.platform.objecttype.web.dto;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ObjectTypeDto {
    private final int objectType;
    private final String code;
    private final String name;
    private final String domain;
    private final String status;
    private final String description;
    private final String createdBy;
    private final long createdById;
    private final OffsetDateTime createdAt;
    private final String updatedBy;
    private final long updatedById;
    private final OffsetDateTime updatedAt;
}

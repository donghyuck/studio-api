package studio.one.platform.objecttype.web.dto;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ObjectTypePolicyDto {
    private final int objectType;
    private final Integer maxFileMb;
    private final String allowedExt;
    private final String allowedMime;
    private final String policyJson;
    private final String createdBy;
    private final long createdById;
    private final OffsetDateTime createdAt;
    private final String updatedBy;
    private final long updatedById;
    private final OffsetDateTime updatedAt;
}

package studio.one.platform.objecttype.web.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ObjectTypeEffectivePolicyDto {
    private final int objectType;
    private final Integer maxFileMb;
    private final String allowedExt;
    private final String allowedMime;
    private final String policyJson;
    private final String source;
}

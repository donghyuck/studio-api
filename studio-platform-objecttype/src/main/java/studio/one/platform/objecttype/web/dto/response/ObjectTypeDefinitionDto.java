package studio.one.platform.objecttype.web.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ObjectTypeDefinitionDto {
    private final ObjectTypeDto type;
    private final ObjectTypePolicyDto policy;
}

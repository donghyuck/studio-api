package studio.one.platform.objecttype.web.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ValidateUploadResponse {
    private final boolean allowed;
    private final String reason;
}

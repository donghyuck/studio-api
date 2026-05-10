package studio.one.base.user.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanySelfJoinRequestCreateRequest(
        @NotBlank @Size(max = 128) String memberKey,
        @Size(max = 1000) String message) {
}

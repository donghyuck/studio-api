package studio.one.base.user.web.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CompanySelfJoinRequestCreateRequest {
    @NotBlank @Size(max = 128) private String memberKey;
    @Size(max = 1000) private String message;

    public CompanySelfJoinRequestCreateRequest() {
    }

    public CompanySelfJoinRequestCreateRequest(@NotBlank @Size(max = 128) String memberKey, @Size(max = 1000) String message) {
        this.memberKey = memberKey;
        this.message = message;
    }

    public String memberKey() {
        return memberKey;
    }

    public String message() {
        return message;
    }

    public String getMemberKey() {
        return memberKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMemberKey(String memberKey) {
        this.memberKey = memberKey;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

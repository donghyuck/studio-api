package studio.one.base.user.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMembersRequest {

    @NotEmpty
    @JsonProperty("userIds")
    private List<Long> userIds;
}

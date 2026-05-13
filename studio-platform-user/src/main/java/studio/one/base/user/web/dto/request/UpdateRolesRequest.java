package studio.one.base.user.web.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRolesRequest {

    @NotNull
    @JsonProperty("roleIds")
    private List<Long> roleIds;
}

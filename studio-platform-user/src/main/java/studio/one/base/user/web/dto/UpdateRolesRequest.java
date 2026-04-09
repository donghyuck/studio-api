package studio.one.base.user.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRolesRequest {

    @NotNull
    @JsonProperty("roleIds")
    private List<Long> roleIds;
}

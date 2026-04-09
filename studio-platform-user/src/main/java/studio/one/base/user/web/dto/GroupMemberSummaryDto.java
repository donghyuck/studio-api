package studio.one.base.user.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupMemberSummaryDto {

    Long userId;
    String username;
    String name;
    boolean enabled;
}

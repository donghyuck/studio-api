package studio.one.base.user.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GroupMemberSummaryResult {
    Long userId;
    String username;
    String name;
    boolean enabled;
}

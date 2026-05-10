package studio.one.base.user.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MePasswordChangeCommand {
    String currentPassword;
    String newPassword;
}

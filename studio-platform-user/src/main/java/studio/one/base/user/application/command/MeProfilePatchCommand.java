package studio.one.base.user.application.command;

import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MeProfilePatchCommand {
    String name;
    String email;
    Boolean emailVisible;
    Boolean nameVisible;
    String lastName;
    String firstName;
    Map<String, String> properties;
}

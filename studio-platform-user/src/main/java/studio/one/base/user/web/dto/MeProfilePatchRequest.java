package studio.one.base.user.web.dto;

import java.util.Map;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class MeProfilePatchRequest {

    @Size(max = 100)
    private String name;

    @Email
    @Size(max = 255)
    private String email;

    private Boolean emailVisible;
    private Boolean nameVisible;

    @Size(max = 100)
    private String lastName;

    @Size(max = 100)
    private String firstName;

    private Map<String, String> properties;
}

package studio.one.platform.security.autoconfigure;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FormLoginProperties {

    private boolean enabled = false;
    private String loginPage = "/login";
    private String loginProcessingUrl = "/login";
    private String usernameParameter = "username";
    private String passwordParameter = "password";
    private String defaultSuccessUrl = "/";
}

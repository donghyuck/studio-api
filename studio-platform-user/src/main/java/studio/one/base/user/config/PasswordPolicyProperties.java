package studio.one.base.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.User.PREFIX + ".password-policy")
@Getter
@Setter
public class PasswordPolicyProperties {

    private int minLength = 8;
    private int maxLength = 20;
    private boolean requireUpper = false;
    private boolean requireLower = false;
    private boolean requireDigit = false;
    private boolean requireSpecial = false;
    private String allowedSpecials = "!@#$%^&*";
    private boolean allowWhitespace = false;
}

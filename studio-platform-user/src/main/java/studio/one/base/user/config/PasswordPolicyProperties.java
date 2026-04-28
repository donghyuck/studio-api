package studio.one.base.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = PasswordPolicyProperties.PREFIX)
@Getter
@Setter
public class PasswordPolicyProperties {

    public static final String PREFIX = "studio.user.password-policy";
    public static final String LEGACY_PREFIX = "studio.features.user.password-policy";

    private int minLength = 8;
    private int maxLength = 20;
    private boolean requireUpper = false;
    private boolean requireLower = false;
    private boolean requireDigit = false;
    private boolean requireSpecial = false;
    private String allowedSpecials = "!@#$%^&*";
    private boolean allowWhitespace = false;
}

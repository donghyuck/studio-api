package studio.one.base.user.web.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordPolicyDto {
    private final int minLength;
    private final int maxLength;
    private final boolean requireUpper;
    private final boolean requireLower;
    private final boolean requireDigit;
    private final boolean requireSpecial;
    private final String allowedSpecials;
    private final boolean allowWhitespace;
}

package studio.one.base.user.application.result;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PasswordPolicyResult {
    int minLength;
    int maxLength;
    boolean requireUpper;
    boolean requireLower;
    boolean requireDigit;
    boolean requireSpecial;
    String allowedSpecials;
    boolean allowWhitespace;
}

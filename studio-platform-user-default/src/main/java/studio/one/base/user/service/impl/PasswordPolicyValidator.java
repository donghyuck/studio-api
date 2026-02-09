package studio.one.base.user.service.impl;

import org.apache.commons.lang3.StringUtils;

import studio.one.base.user.config.PasswordPolicyProperties;
import studio.one.base.user.exception.PasswordPolicyViolationException;
import studio.one.base.user.service.PasswordPolicyService;
import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;

public class PasswordPolicyValidator implements PasswordPolicyService {

    private final PasswordPolicyProperties properties;
    private final I18n i18n;

    public PasswordPolicyValidator(PasswordPolicyProperties properties, I18n i18n) {
        this.properties = (properties != null) ? properties : new PasswordPolicyProperties();
        this.i18n = i18n;
    }

    @Override
    public PasswordPolicyDto getPolicy() {
        return PasswordPolicyDto.builder()
                .minLength(properties.getMinLength())
                .maxLength(properties.getMaxLength())
                .requireUpper(properties.isRequireUpper())
                .requireLower(properties.isRequireLower())
                .requireDigit(properties.isRequireDigit())
                .requireSpecial(properties.isRequireSpecial())
                .allowedSpecials(properties.getAllowedSpecials())
                .allowWhitespace(properties.isAllowWhitespace())
                .build();
    }

    public PasswordPolicyDto toDto() {
        return getPolicy();
    }

    @Override
    public void validate(String password) {
        if (password == null) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.required"));
        }
        String pwd = password;
        if (!properties.isAllowWhitespace() && StringUtils.containsWhitespace(pwd)) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.whitespace.not.allowed"));
        }
        int len = pwd.length();
        if (len < properties.getMinLength()) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.too.short"));
        }
        if (len > properties.getMaxLength()) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.too.long"));
        }
        if (properties.isRequireUpper() && !containsUpper(pwd)) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.require.upper"));
        }
        if (properties.isRequireLower() && !containsLower(pwd)) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.require.lower"));
        }
        if (properties.isRequireDigit() && !containsDigit(pwd)) {
            throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.require.digit"));
        }
        if (properties.isRequireSpecial()) {
            String specials = StringUtils.defaultString(properties.getAllowedSpecials());
            if (!containsSpecial(pwd, specials)) {
                throw PasswordPolicyViolationException.of(resolveReason("password.policy.reason.password.require.special"));
            }
        }
    }

    private String resolveReason(String key) {
        if (i18n == null) {
            return key;
        }
        return I18nUtils.safeGet(i18n, key, key);
    }

    private boolean containsUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLower(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSpecial(String s, String allowed) {
        if (StringUtils.isBlank(allowed)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (allowed.indexOf(s.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}

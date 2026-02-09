package studio.one.base.user.service;

import studio.one.base.user.web.dto.PasswordPolicyDto;

public interface PasswordPolicyService {

    PasswordPolicyDto getPolicy();

    void validate(String password);
}

package studio.one.base.user.application.usecase;

import studio.one.base.user.web.dto.response.PasswordPolicyDto;

public interface PasswordPolicyService {

    PasswordPolicyDto getPolicy();

    void validate(String password);
}

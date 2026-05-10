package studio.one.base.user.application.usecase;

import studio.one.base.user.application.result.PasswordPolicyResult;

public interface PasswordPolicyService {

    PasswordPolicyResult getPolicy();

    void validate(String password);
}

package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;

import studio.one.base.user.application.usecase.PasswordPolicyService;
import studio.one.base.user.web.dto.response.PasswordPolicyDto;
import studio.one.platform.web.dto.ApiResponse;

public abstract class AbstractPasswordPolicyControllerSupport {

    protected ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicyResponse(PasswordPolicyService service) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPolicy()));
    }
}

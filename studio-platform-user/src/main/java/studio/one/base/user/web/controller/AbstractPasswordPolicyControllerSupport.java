package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;

import studio.one.base.user.service.PasswordPolicyService;
import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.platform.web.dto.ApiResponse;

public abstract class AbstractPasswordPolicyControllerSupport {

    protected ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicyResponse(PasswordPolicyService service) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPolicy()));
    }
}

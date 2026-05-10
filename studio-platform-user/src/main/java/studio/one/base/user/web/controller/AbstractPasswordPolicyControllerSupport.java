package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;

import studio.one.base.user.application.result.PasswordPolicyResult;
import studio.one.base.user.application.usecase.PasswordPolicyService;
import studio.one.base.user.web.dto.response.PasswordPolicyDto;
import studio.one.platform.web.dto.ApiResponse;

public abstract class AbstractPasswordPolicyControllerSupport {

    protected ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicyResponse(PasswordPolicyService service) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(service.getPolicy())));
    }

    private PasswordPolicyDto toDto(PasswordPolicyResult result) {
        return PasswordPolicyDto.builder()
                .minLength(result.getMinLength())
                .maxLength(result.getMaxLength())
                .requireUpper(result.isRequireUpper())
                .requireLower(result.isRequireLower())
                .requireDigit(result.isRequireDigit())
                .requireSpecial(result.isRequireSpecial())
                .allowedSpecials(result.getAllowedSpecials())
                .allowWhitespace(result.isAllowWhitespace())
                .build();
    }
}

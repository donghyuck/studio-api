package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.service.PasswordPolicyService;
import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.base.user.web.controller.AbstractPasswordPolicyControllerSupport;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class UserAuthPublicController extends AbstractPasswordPolicyControllerSupport implements UserAuthPublicControllerApi {

    private final PasswordPolicyService passwordPolicyService;

    @GetMapping("/password-policy")
    @Override
    public ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy() {
        return passwordPolicyResponse(passwordPolicyService);
    }
}

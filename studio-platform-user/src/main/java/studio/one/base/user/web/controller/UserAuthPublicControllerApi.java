package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;

import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.platform.web.dto.ApiResponse;

public interface UserAuthPublicControllerApi {

    ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy();
}

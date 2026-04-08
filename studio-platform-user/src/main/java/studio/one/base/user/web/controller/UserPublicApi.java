package studio.one.base.user.web.controller;

import org.springframework.http.ResponseEntity;

import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.base.user.web.dto.UserPublicDto;
import studio.one.platform.web.dto.ApiResponse;

public interface UserPublicApi {

    ResponseEntity<ApiResponse<UserPublicDto>> getByName(String name);

    ResponseEntity<ApiResponse<UserPublicDto>> getById(Long id);

    ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy();
}

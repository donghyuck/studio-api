package studio.one.base.user.web.controller;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import studio.one.base.user.web.dto.ChangePasswordRequest;
import studio.one.base.user.web.dto.CreateUserRequest;
import studio.one.base.user.web.dto.DisableUserRequest;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.dto.UpdateUserRequest;
import studio.one.base.user.web.dto.UserBasicDto;
import studio.one.base.user.web.dto.UserDto;
import studio.one.platform.web.dto.ApiResponse;

public interface UserMgmtControllerApi {

    ResponseEntity<ApiResponse<Long>> register(@Valid @RequestBody CreateUserRequest request,
            HttpServletRequest http);

    ResponseEntity<ApiResponse<Page<UserDto>>> list(
            @RequestParam(value = "q", required = false) Optional<String> q,
            Pageable pageable);

    ResponseEntity<ApiResponse<Page<UserBasicDto>>> listBasic(
            @RequestParam(value = "q", required = false) Optional<String> q,
            Pageable pageable);

    ResponseEntity<ApiResponse<Page<UserDto>>> find(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "requireQuery", required = false, defaultValue = "true") boolean requireQuery,
            Pageable pageable);

    ResponseEntity<ApiResponse<UserDto>> get(Long id);

    ResponseEntity<ApiResponse<UserBasicDto>> getBasic(Long id);

    ResponseEntity<ApiResponse<UserDto>> update(Long id, @RequestBody UpdateUserRequest req);

    ResponseEntity<Void> passwordReset(Long id, UserDetails actor);

    ResponseEntity<Void> passwordReset(Long id, @Valid @RequestBody ChangePasswordRequest req,
            UserDetails actor);

    ResponseEntity<Void> enable(Long id, UserDetails actor);

    ResponseEntity<Void> disable(Long id, DisableUserRequest req, UserDetails actor);

    ResponseEntity<ApiResponse<List<RoleDto>>> roles(Long id, String by);

    ResponseEntity<ApiResponse<Void>> updateUserRoles(Long id, @RequestBody List<RoleDto> roles,
            UserDetails actor);
}

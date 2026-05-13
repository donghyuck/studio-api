package studio.one.base.user.web.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import studio.one.base.user.web.dto.request.ChangePasswordRequest;
import studio.one.base.user.web.dto.request.CreateUserRequest;
import studio.one.base.user.web.dto.request.DisableUserRequest;
import studio.one.base.user.web.dto.response.PasswordPolicyDto;
import studio.one.base.user.web.dto.response.PropertyDto;
import studio.one.base.user.web.dto.response.RoleDto;
import studio.one.base.user.web.dto.request.UpdateRolesRequest;
import studio.one.base.user.web.dto.request.UpdateUserRequest;
import studio.one.base.user.web.dto.response.UserBasicDto;
import studio.one.base.user.web.dto.response.UserDto;
import studio.one.platform.web.dto.ApiResponse;

public interface UserMgmtApi {

    ResponseEntity<ApiResponse<Long>> register(@Valid @RequestBody CreateUserRequest request,
            HttpServletRequest http);

    ResponseEntity<ApiResponse<Page<UserDto>>> list(
            @RequestParam(value = "q", required = false) Optional<String> q,
            Pageable pageable);

    default ResponseEntity<ApiResponse<Page<UserDto>>> list(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "companyId", required = false) Optional<Long> companyId,
            UserDetails principal,
            Pageable pageable) {
        return list(q, pageable);
    }

    ResponseEntity<ApiResponse<Page<UserBasicDto>>> listBasic(
            @RequestParam(value = "q", required = false) Optional<String> q,
            Pageable pageable);

    default ResponseEntity<ApiResponse<Page<UserBasicDto>>> listBasic(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "companyId", required = false) Optional<Long> companyId,
            UserDetails principal,
            Pageable pageable) {
        return listBasic(q, pageable);
    }

    ResponseEntity<ApiResponse<Page<UserDto>>> find(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "requireQuery", required = false, defaultValue = "true") boolean requireQuery,
            Pageable pageable);

    default ResponseEntity<ApiResponse<Page<UserDto>>> find(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "companyId", required = false) Optional<Long> companyId,
            @RequestParam(value = "requireQuery", required = false, defaultValue = "true") boolean requireQuery,
            UserDetails principal,
            Pageable pageable) {
        return find(q, requireQuery, pageable);
    }

    ResponseEntity<ApiResponse<UserDto>> get(Long id);

    ResponseEntity<ApiResponse<UserBasicDto>> getBasic(Long id);

    ResponseEntity<ApiResponse<UserDto>> update(Long id, @RequestBody UpdateUserRequest req);

    default ResponseEntity<Void> delete(Long id) {
        throw new UnsupportedOperationException("User delete endpoint is not implemented");
    }

    ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy();

    ResponseEntity<Void> passwordReset(Long id, @Valid @RequestBody ChangePasswordRequest req,
            UserDetails actor);

    ResponseEntity<Void> enable(Long id, UserDetails actor);

    ResponseEntity<Void> disable(Long id, DisableUserRequest req, UserDetails actor);

    ResponseEntity<ApiResponse<List<RoleDto>>> roles(Long id, String by);

    ResponseEntity<ApiResponse<Void>> updateUserRoles(Long id, @RequestBody UpdateRolesRequest req,
            UserDetails actor);

    // Properties
    ResponseEntity<ApiResponse<Map<String, String>>> getProperties(Long id);

    ResponseEntity<ApiResponse<Map<String, String>>> replaceProperties(Long id,
            @RequestBody Map<String, String> properties);

    ResponseEntity<ApiResponse<PropertyDto>> getProperty(Long id, String key);

    ResponseEntity<ApiResponse<PropertyDto>> setProperty(Long id, String key,
            @Valid @RequestBody PropertyDto dto);

    ResponseEntity<Void> deleteProperty(Long id, String key);
}

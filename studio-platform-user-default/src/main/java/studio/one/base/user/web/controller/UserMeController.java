package studio.one.base.user.web.controller;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import javax.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.service.PasswordPolicyService;
import studio.one.base.user.web.controller.AbstractPasswordPolicyControllerSupport;
import studio.one.base.user.web.dto.MeProfileDto;
import studio.one.base.user.web.dto.MeProfilePatchRequest;
import studio.one.base.user.web.dto.MeProfilePutRequest;
import studio.one.base.user.web.dto.MePasswordChangeRequest;
import studio.one.base.user.web.dto.PasswordPolicyDto;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.Self.PATH + ":/api/self}")
@RequiredArgsConstructor
@Slf4j
public class UserMeController extends AbstractPasswordPolicyControllerSupport implements UserMeControllerApi {
    private final ApplicationUserService<User, Role> userService;
    private final ApplicationUserMapper mapper;
    private final PasswordPolicyService passwordPolicyService;

    @GetMapping("")
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ApiResponse<MeProfileDto>> me(@AuthenticationPrincipal UserDetails principal) {
        String username = requirePrincipal(principal);
        User user = userService.findByUsername(username)
                .orElseThrow(
                        () -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
        Set<Role> roles = userService.findEffectiveRoles(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(user, roles)));
    }

    @PatchMapping("")
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ApiResponse<MeProfileDto>> patchMe(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MeProfilePatchRequest request) {
        String username = requirePrincipal(principal);
        User updated = userService.updateSelfByUsername(username, request);
        return ResponseEntity.ok(ApiResponse.ok(toDto(updated, null)));
    }

    @PutMapping("")
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ApiResponse<MeProfileDto>> putMe(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MeProfilePutRequest request) {
        String username = requirePrincipal(principal);
        User updated = userService.replaceSelfByUsername(username, request);
        return ResponseEntity.ok(ApiResponse.ok(toDto(updated, null)));
    }

    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody MePasswordChangeRequest request) {
        String username = requirePrincipal(principal);
        userService.changeSelfPasswordByUsername(username, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/password-policy")
    @PreAuthorize("isAuthenticated()")
    @Override
    public ResponseEntity<ApiResponse<PasswordPolicyDto>> passwordPolicy(
            @AuthenticationPrincipal UserDetails principal) {
        requirePrincipal(principal);
        return passwordPolicyResponse(passwordPolicyService);
    }

    private String requirePrincipal(UserDetails principal) {
        if (principal == null || principal.getUsername() == null)
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Unauthenticated");
        return principal.getUsername();
    }

    private MeProfileDto toDto(User u, Set<Role> roles) {
        return MeProfileDto.builder()
                .userId(u.getUserId())
                .username(u.getUsername())
                .name(u.getName())
                .email(u.getEmail())
                .enabled(u.isEnabled())
                .roles(roles == null ? List.of()
                        : roles.stream()
                                .filter(Objects::nonNull)
                                .map(Role::getName)
                                .filter(Objects::nonNull)
                                .sorted()
                                .collect(Collectors.toList()))
                .createdAt(toOffset(u.getCreationDate()))
                .updatedAt(toOffset(u.getModifiedDate()))
                .build();
    }

    private OffsetDateTime toOffset(Instant d) {
        return d == null ? null : d.atOffset(ZoneOffset.UTC);
    }
}
